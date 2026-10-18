/*
 * Copyright (C) 2024 AE2 Enthusiast
 *
 * This file is part of MAE2.
 *
 * MAE2 is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * MAE2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see https://www.gnu.org/licenses/.
 */
package stone.mae2.parts.p2p.multi;

import appeng.api.config.PowerUnit;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.p2p.P2PModels;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.compat.FeCompat;
import com.gregtechceu.gtceu.api.capability.GTCapability;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.capabilities.BlockCapability;

import stone.mae2.MAE2;
import stone.mae2.util.TransferMath;
import stone.mae2.api.Tickable;
import stone.mae2.me.service.MultiP2PService;

import java.util.List;

public class EUMultiP2PTunnel extends
  CapabilityMultiP2PTunnel<EUMultiP2PTunnel, EUMultiP2PTunnel.Logic, EUMultiP2PTunnel.Part, IEnergyContainer>
  implements Tickable {
  private static final IEnergyContainer NULL_ENERGY_STORAGE = new NullHandler();

  /**
   * Buffer of energy used to separate inputs from outputs
   * 
   * P2P inputs dump into this which then gets distributed out to each P2P
   * output. This is intended to reduce the computational complexity of the P2P.
   * Otherwise if each input maps to each output, the number of operations goes
   * quadratic
   */
  // Persisted
  private final stone.mae2.util.SharedEnergy buffer = new stone.mae2.util.SharedEnergy();

  // prevents divide by zero errors
  private long maxVoltage = 1;

  /**
   * Flag for if we have enough EU in the buffer for another distribution
   * 
   * Set every distribution if there's more left in buffer then was distributed.
   * P2P input's won't accept EU while this is set.
   */
  private boolean isSatisfied = false;

  public EUMultiP2PTunnel(short freq, IGrid grid) {
    super(freq, grid);
    this.inputHandler = new InputHandler();
    this.outputHandler = new OutputHandler();
    this.emptyHandler = NULL_ENERGY_STORAGE;
  }

  @Override
  public CompoundTag saveNodeData(Part part) {
    CompoundTag data = super.saveNodeData(part);
    if (data == null) {
      data = new CompoundTag();
    }
    saveEnergy(data, FEMultiP2PTunnel.ENERGY_TAG, energyShare(part, buffer.exact()));
    data.putLong("voltage", maxVoltage);
    return data;
  }

  @Override
  public Logic addTunnel(Part part, Tag tag) {
    if (tag instanceof CompoundTag data)
      this.buffer.add(readEnergy(data, FEMultiP2PTunnel.ENERGY_TAG));
    if (tag instanceof CompoundTag data) maxVoltage = Math.max(maxVoltage, data.getLong("voltage"));
    return addTunnel(part);
  }

  @Override
  public boolean removeTunnel(Part part) {
    var share = energyShare(part, buffer.exact());
    boolean removed = super.removeTunnel(part);
    if (removed) buffer.remove(share);
    return removed;
  }

  public static class Part extends
    CapabilityMultiP2PTunnel.Part<EUMultiP2PTunnel, Logic, Part, IEnergyContainer> {
    private static final P2PModels MODELS = new P2PModels(
      MAE2.toKey("part/p2p/multi_p2p_tunnel_eu"));

    public Part(IPartItem<?> partItem) {
      super(partItem);
      if (MAE2.CONFIG.parts().isEUP2PNerfed())
        this
          .getMainNode()
          .setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.COMPRESSED_CHANNEL);
    }

    @PartModels
    public static List<IPartModel> getModels() { return MODELS.getModels(); }

    @Override
    public IPartModel getStaticModels() {
      return MODELS.getModel(this.isPowered(), this.isActive());
    }

    @Override
    public EUMultiP2PTunnel createTunnel(short freq) {
      return new EUMultiP2PTunnel(freq, this.getGridNode().getGrid());
    }

    @Override
    public Class<EUMultiP2PTunnel> getTunnelClass() {
      return EUMultiP2PTunnel.class;
    }
  }

  public class Logic extends
    CapabilityMultiP2PTunnel<EUMultiP2PTunnel, Logic, Part, IEnergyContainer>.Logic {
    public Logic(Part part) {
      super(part);
    }
  }

  private class InputHandler implements IEnergyContainer {
    @Override
    public long acceptEnergyFromNetwork(Direction side, long voltage,
      long amperage) {
      if (isSatisfied || !grid.getService(MultiP2PService.class).taxSatisfied) return 0;
      long accepted = TransferMath.acceptedPackets(buffer.stored(), voltage, amperage);
      if (accepted == 0) return 0;
      maxVoltage = Math.max(maxVoltage, voltage);
      buffer.add(voltage * accepted);
      markPersistentDataChanged();
      return accepted;
    }

    @Override
    public boolean inputsEnergy(Direction side) {
      return true;
    }

    @Override
    public long changeEnergy(long differenceAmount) {
      return 0;
    }

    @Override
    public long getEnergyStored() { return 0; }

    @Override
    public long getEnergyCapacity() { return Long.MAX_VALUE; }

    @Override
    public long getInputAmperage() { return Long.MAX_VALUE; }

    @Override
    public long getInputVoltage() { return Long.MAX_VALUE; }
  }

  private class OutputHandler implements IEnergyContainer {
    @Override
    public long acceptEnergyFromNetwork(Direction side, long voltage,
      long amperage) {
      return 0;
    }

    @Override
    public boolean inputsEnergy(Direction side) {
      return false;
    }

    @Override
    public long changeEnergy(long differenceAmount) {
      return 0;
    }

    @Override
    public long getEnergyStored() { return buffer.stored(); }

    @Override
    public long getEnergyCapacity() { return Long.MAX_VALUE; }

    @Override
    public long getInputAmperage() { return 0; }

    @Override
    public long getInputVoltage() { return 0; }
  }

  private static class NullHandler implements IEnergyContainer {
    @Override
    public long acceptEnergyFromNetwork(Direction side, long voltage,
      long amperage) {
      return 0;
    }

    @Override
    public boolean inputsEnergy(Direction side) {
      return false;
    }

    @Override
    public long changeEnergy(long differenceAmount) {
      return 0;
    }

    @Override
    public long getEnergyStored() { return 0; }

    @Override
    public long getEnergyCapacity() { return 0; }

    @Override
    public long getInputAmperage() { return 0; }

    @Override
    public long getInputVoltage() { return 0; }
  }

  @Override
  protected BlockCapability<IEnergyContainer, Direction> getCapability() {
    return GTCapability.CAPABILITY_ENERGY_CONTAINER;
  }

  @Override
  public Logic createLogic(Part part) {
    return part.setLogic(new Logic(part));
  }

  @Override
  public TickingRequest getTickingRequest() {
    return Tickable
      .toTickingRequest(MAE2.CONFIG.parts().rates().EUMultiP2PTunnel(), false,
        false);
  }

  @Override
  public TickRateModulation tick() {
    MultiP2PService service = this.grid.getService(MultiP2PService.class);
    if (!service.taxSatisfied) {
      this.isSatisfied = true;
      return TickRateModulation.IDLE;
    }
    boolean didWork = false;
    double distributed = 0;
    long voltage = maxVoltage;
    var endpoints = endpointSnapshot();
    for (var output : java.util.List.copyOf(this.outputs)) {
      try (var guard = output.getAdjacentCapability()) {
        long offered = buffer.packets(voltage);
        if (offered <= 0) break;
        var reserved = java.math.BigInteger.valueOf(offered).multiply(java.math.BigInteger.valueOf(voltage));
        buffer.remove(reserved);
        long inserted = 0;
        try {
          inserted = TransferMath.clampAccepted(offered, guard.get().acceptEnergyFromNetwork(
              output.part.getSide().getOpposite(), voltage, offered));
        } finally {
          var refund = reserved.subtract(java.math.BigInteger.valueOf(inserted).multiply(java.math.BigInteger.valueOf(voltage)));
          refundEnergy(refund, endpoints, (tunnel, amount) -> {
            tunnel.buffer.add(amount);
            tunnel.maxVoltage = Math.max(tunnel.maxVoltage, voltage);
            tunnel.markPersistentDataChanged();
          });
        }
        didWork |= inserted > 0;
        distributed += inserted;
      }
    }
    // stop at double what's being transferred or one amp (whichever is higher)
    // (enough to
    // bootstrap the unsatisfaction)
    this.isSatisfied = this.buffer.asDouble() / maxVoltage / 2 > distributed
      && this.buffer.packets(maxVoltage) > 0;
    if (distributed > 0) {
      markPersistentDataChanged();
      if (MAE2.CONFIG.parts().isEUP2PNerfed()) {
        int tier = (int) Math
          .min(GTValues.TIER_COUNT - 1,
            Math.round(Math.log1p(voltage / 8) / Math.log(4)));
        service.transferredAmps[tier] += distributed;
      } else
        this
          .deductEnergyCost(distributed * voltage * FeCompat.ratio(false),
            PowerUnit.FE);
      if (buffer.isEmpty()) this.maxVoltage = 1;
    }
    return didWork ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
  }
}
