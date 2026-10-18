package stone.mae2.parts.p2p;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.AECapabilities;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import stone.mae2.parts.p2p.PatternTargetCache;
import appeng.items.parts.PartModels;
import appeng.me.helpers.MachineSource;
import appeng.parts.p2p.P2PModels;
import appeng.parts.p2p.P2PTunnelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;


import stone.mae2.MAE2;
import stone.mae2.parts.p2p.PatternP2PPartLogic.PatternP2PPartLogicHost;
import stone.mae2.parts.p2p.PatternP2PTunnelLogic.PatternP2PTunnel;
import stone.mae2.parts.p2p.PatternP2PTunnelLogic.Target;
import stone.mae2.util.TransHelper;

import java.util.List;

public class PatternP2PTunnelPart extends P2PTunnelPart<PatternP2PTunnelPart>
  implements PatternP2PTunnel, PatternP2PPartLogicHost {
  private static final P2PModels MODELS = new P2PModels(MAE2.toKey("part/p2p/p2p_tunnel_pattern"));

  protected final IActionSource source;
  protected final java.util.function.Supplier<PatternP2PTunnelLogic> logic;

  // Prevents recursive block updates.
  private boolean inBlockUpdate = false;
  private PatternTargetCache targetCache;
  private final PatternP2PPartLogic partLogic = new PatternP2PPartLogic(this);

  public PatternP2PTunnelPart(IPartItem<?> partItem) {
    super(partItem);
    this.source = new MachineSource(this);
    // isOutput() is false because nbt was not read yet
    this.logic = com.google.common.base.Suppliers.memoize(() -> new PatternP2PTunnelLogic(this));
  }

  @Override
  public void readFromNBT(CompoundTag data, net.minecraft.core.HolderLookup.Provider registries) {
    super.readFromNBT(data, registries);
    this.partLogic.readFromNBT(data, registries);
  }

  @Override
  public void writeToNBT(CompoundTag data, net.minecraft.core.HolderLookup.Provider registries) {
    super.writeToNBT(data, registries);
    this.partLogic.writeToNBT(data, registries);
  }

  @Override
  public void addToWorld() {
    super.addToWorld();
    this.targetCache = PatternP2PPartLogicHost.super.getCache();
    if (this.isOutput()) {
      PatternP2PTunnelPart input = this.getInput();
      if (input != null) 
        input.logic.get().refreshOutputs();
    }
  }

  @Override
  public void removeFromWorld() {
    super.removeFromWorld();
    if (this.isOutput()) {
      PatternP2PTunnelPart input = this.getInput();
      if (input != null) 
        input.logic.get().refreshOutputs();
    }
  }

  public boolean canAcceptPattern() {
    // deliberately the node's isActive(), not the part's: AEBasePart.isActive()
    // is the visual one and ignores grid booting, so it stays true for the few
    // ticks between a connection being cut and the repath finishing
    IGridNode node = this.getGridNode();
    return node != null && node.isActive()
      && this.partLogic.isSendListEmpty();
  }

  public void addToSendList(AEKey what, long l) {
    this.partLogic.addToSendList(what, l);
  }

  public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
    this.partLogic.addAdditionalDrops(drops, wrenched);
  }

  @Override
  public PatternTargetCache getCache() { return this.targetCache; }

  private boolean isRecursive = false;

  @Override
  public PatternContainerGroup getGroup() {
    if (isRecursive) {
      return new PatternContainerGroup(AEItemKey.of(Items.BARRIER),
        TransHelper.GUI.translatable("patternP2P.recursive"), List.of());
    }
    try {
      isRecursive = true;
      PatternContainerGroup group = PatternP2PTunnel.super.getGroup();
      // always called on the input part anyways, no need to get it
      if (this.hasCustomName())
        return new PatternContainerGroup(group.icon(),
          TransHelper.GUI
            .translatable("patternP2P.aggregate", this.getCustomName(),
              this.getOutputs().size()),
          group.tooltip());
      else
        return group;
    } finally {
      isRecursive = false;
    }
  }

  @PartModels
  public static List<IPartModel> getModels() { return MODELS.getModels(); }

  public IPartModel getStaticModels() {
    return MODELS.getModel(this.isPowered(), this.isActive());
  }

  public <T> T getCapability(BlockCapability<T, Direction> capability) {
    if (!getMainNode().isActive() || getFrequency() == 0) return null;
    if (!isOutput()) return capability == AECapabilities.CRAFTING_MACHINE ? (T) logic.get() : null;
    var input = getInput();
    if (input == null || !input.level().hasChunkAt(input.pos())) return null;
    return input.level().getCapability(capability, input.pos(), input.side());
  }

  @Override
  public List<Target> getPatternTunnelInputs() {
    Target input = this.isOutput() ? this.getInput() : this;
    return input == null ? List.of() : List.of(input);
  }

  @Override
  public List<? extends Target> getPatternTunnelOutputs() {
    return this.getOutputs();
  }

  @Override
  public ServerLevel level() {
    // this has to be a server level
    // if it isn't there's nothing else to do really
    // returning null would just throw a NPE eventually
    return (ServerLevel) this.getLevel();
  }

  @Override
  public BlockPos pos() {
    return this.getBlockEntity().getBlockPos().relative(this.getSide());
  }

  @Override
  public Direction side() {
    return this.getSide().getOpposite();
  }

  @Override
  public IActionSource source() {
    return this.source;
  }

  /**
   * The position right in front of this P2P tunnel.
   */
  private BlockPos getFacingPos() {
    return getHost().getLocation().getPos().relative(getSide());
  }

  // Send a block update on p2p status change, or any update on another endpoint.
  protected void sendBlockUpdate() {
    // Prevent recursive block updates.
    if (!inBlockUpdate) {
      inBlockUpdate = true;

      try {
        // getHost().notifyNeighbors() would queue a callback, but we want to do an update synchronously!
        // (otherwise we can't detect infinite recursion, it would just queue updates endlessly)
        getHost().notifyNeighborNow(getSide());
      } finally {
        inBlockUpdate = false;
      }
    }
  }
  
  /**
   * Forward block updates from the attached tile's position to the other end of the tunnel. Required for TE's on the
   * other end to know that the available caps may have changed.
   */
  @Override
  public void onNeighborChanged(BlockGetter level, BlockPos pos, BlockPos neighbor) {
    // We only care about block updates on the side this tunnel is facing
    if (!this.getFacingPos().equals(neighbor)) {
      return;
    }

    // Prevent recursive block updates.
    if (!inBlockUpdate) {
      inBlockUpdate = true;

      try {
        if (isOutput()) {
          PatternP2PTunnelPart input = getInput();

          if (input != null) {
            input.sendBlockUpdate();
          }
        } else {
          for (PatternP2PTunnelPart output : this.getOutputs()) {
            output.sendBlockUpdate();
          }
        }
      } finally {
        inBlockUpdate = false;
      }
    }
  }
}
