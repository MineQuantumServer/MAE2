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
import appeng.api.networking.IGrid;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.p2p.P2PModels;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import stone.mae2.MAE2;
import stone.mae2.util.TransferMath;
import stone.mae2.api.Tickable;

import java.util.List;

public class FEMultiP2PTunnel extends
  CapabilityMultiP2PTunnel<FEMultiP2PTunnel, FEMultiP2PTunnel.Logic, FEMultiP2PTunnel.Part, IEnergyStorage>
  implements Tickable {
  private static final IEnergyStorage NULL_ENERGY_STORAGE = new NullHandler();

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

  /**
   * How much FE has been extracted in between distributions
   * 
   * This is needed because it's possible to extract energy from the buffer via
   * the outputs of the tunnel. This'd bypass the normal distribution logic, and
   * thus needs to be accounted for.
   */
  private final stone.mae2.util.SharedEnergy extracted = new stone.mae2.util.SharedEnergy();

  /**
   * Flag for if we have enough FE in the buffer for another distribution
   * 
   * Set every distribution if there's more left in buffer then was distributed.
   * P2P input's won't accept FE while this is set.
   */
  private boolean isSatisfied;

  public FEMultiP2PTunnel(short freq, IGrid grid) {
    super(freq, grid);
    this.inputHandler = new InputHandler();
    this.outputHandler = new OutputHandler();
    this.emptyHandler = NULL_ENERGY_STORAGE;
  }

  public static final String ENERGY_TAG = "energy";

  @Override
  public CompoundTag saveNodeData(Part part) {
    CompoundTag data = super.saveNodeData(part);
    if (data == null)
      data = new CompoundTag();
    saveEnergy(data, ENERGY_TAG, energyShare(part, buffer.exact()));
    saveEnergy(data, "pendingTax", energyShare(part, extracted.exact()));
    return data;
  }

  @Override
  public Logic addTunnel(Part part, Tag tag) {
    if (tag instanceof CompoundTag data) {
      this.buffer.add(readEnergy(data, ENERGY_TAG));
      this.extracted.add(readEnergy(data, "pendingTax"));
    }
    return addTunnel(part);
  }

  @Override
  public boolean removeTunnel(Part part) {
    var share = energyShare(part, buffer.exact());
    var taxShare = energyShare(part, extracted.exact());
    boolean removed = super.removeTunnel(part);
    if (removed) { buffer.remove(share); extracted.remove(taxShare); }
    return removed;
  }

  public static class Part extends
    CapabilityMultiP2PTunnel.Part<FEMultiP2PTunnel, Logic, Part, IEnergyStorage> {
    private static final P2PModels MODELS = new P2PModels(
      MAE2.toKey("part/p2p/multi_p2p_tunnel_fe"));

    public Part(IPartItem<?> partItem) {
      super(partItem);
    }

    @PartModels
    public static List<IPartModel> getModels() { return MODELS.getModels(); }

    @Override
    public IPartModel getStaticModels() {
      return MODELS.getModel(this.isPowered(), this.isActive());
    }

    @Override
    public FEMultiP2PTunnel createTunnel(short freq) {
      return new FEMultiP2PTunnel(freq, this.getGridNode().getGrid());
    }

    @Override
    public Class<FEMultiP2PTunnel> getTunnelClass() {
      return FEMultiP2PTunnel.class;
    }
  }

  public class Logic extends
    CapabilityMultiP2PTunnel<FEMultiP2PTunnel, Logic, Part, IEnergyStorage>.Logic {

    public Logic(Part part) {
      super(part);
    }
  }

  private class InputHandler implements IEnergyStorage {
    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
      if (isSatisfied || maxReceive <= 0) return 0;
      // Buffer plus untaxed withdrawals stays bounded even with repeated refill/extract calls.
      int accepted = (int) Math.min(maxReceive, buffer.room() - Math.min(buffer.room(), extracted.stored()));
      if (!simulate && accepted > 0) { buffer.add(accepted); markPersistentDataChanged(); }
      return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
      return 0;
    }

    // since this can store past max int, it's nonsensical to translate back to
    // FE's API. Instead just look like it's empty and let others push as much
    // as they want in. Worst case it'll get capped via the satisfaction flag
    @Override
    public int getEnergyStored() { return 0; }

    @Override
    public int getMaxEnergyStored() { return Integer.MAX_VALUE; }

    @Override
    public boolean canExtract() {
      return false;
    }

    @Override
    public boolean canReceive() {
      return true;
    }
  }

  private class OutputHandler implements IEnergyStorage {
    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
      return 0;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
      long energyExtracted = Math.min(buffer.stored(), Math.max(0, maxExtract));
      if (!simulate) {
        buffer.remove(energyExtracted);
        // tax'll come during distribution
        extracted.add(energyExtracted);
        if (energyExtracted > 0) markPersistentDataChanged();
      }
      // cast is okay because energyExtracted will never be above an int
      return (int) energyExtracted;
    }

    @Override
    public int getEnergyStored() {
      try {
        return Math.toIntExact(buffer.stored());
      } catch (ArithmeticException e) {
        return Integer.MAX_VALUE;
      }
    }

    @Override
    public int getMaxEnergyStored() { return Integer.MAX_VALUE; }

    @Override
    public boolean canExtract() {
      return true;
    }

    @Override
    public boolean canReceive() {
      return false;
    }
  }

  private static class NullHandler implements IEnergyStorage {
    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
      return 0;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
      return 0;
    }

    @Override
    public int getEnergyStored() { return 0; }

    @Override
    public int getMaxEnergyStored() { return 0; }

    @Override
    public boolean canExtract() {
      return false;
    }

    @Override
    public boolean canReceive() {
      return false;
    }
  }

  @Override
  protected BlockCapability<IEnergyStorage, Direction> getCapability() {
    return Capabilities.EnergyStorage.BLOCK;
  }

  @Override
  public Logic createLogic(Part part) {
    return part.setLogic(new Logic(part));
  }

  @Override
  public TickingRequest getTickingRequest() {
    return Tickable
      .toTickingRequest(MAE2.CONFIG.parts().rates().FEMultiP2PTunnel(), false,
        false);
  }

  @Override
  public TickRateModulation tick() {
    boolean didWork = false;
    double distributed = this.extracted.asDouble();
    this.extracted.clear();
    var endpoints = endpointSnapshot();
    for (var output : java.util.List.copyOf(this.outputs)) {
      try (var guard = output.getAdjacentCapability()) {
        int toInsert;
        try {
          toInsert = Math.toIntExact(this.buffer.stored());
        } catch (ArithmeticException e) {
          toInsert = Integer.MAX_VALUE;
        }
        buffer.remove(toInsert);
        int inserted = 0;
        try {
          inserted = (int) TransferMath.clampAccepted(toInsert, guard.get().receiveEnergy(toInsert, false));
        } finally {
          refundEnergy(java.math.BigInteger.valueOf(toInsert - inserted), endpoints, (tunnel, amount) -> {
            tunnel.buffer.add(amount);
            tunnel.markPersistentDataChanged();
          });
        }
        didWork |= inserted > 0;
        distributed += inserted;
      }
    }
    if (distributed > 0) markPersistentDataChanged();
    this.isSatisfied = this.buffer.asDouble() / 2 > distributed;
    this.deductEnergyCost(distributed, PowerUnit.FE);
    return didWork ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
  }
}
