package stone.mae2.parts.p2p.multi;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.features.P2PTunnelAttunement;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEKeyType;
import appeng.api.util.AECableType;
import appeng.client.render.cablebus.P2PTunnelFrequencyModelData;
import appeng.core.AEConfig;
import appeng.parts.AEBasePart;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.MemoryCardColors;
import appeng.items.tools.MemoryCardItem;
import net.minecraft.core.component.DataComponentMap;
import appeng.util.Platform;
import appeng.util.SettingsFrom;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import stone.mae2.api.features.MultiP2PTunnelAttunement;
import stone.mae2.me.service.MultiP2PService;

import java.util.Optional;
import java.util.Set;

/*
 * I'm deeply sorry for anyone who has to read and understand this code. I can't
 * think of a better way to do this (though I'm sure I'll think of something
 * perfect once I'm done). On the bright side it's actually pretty nice once you
 * get to the final concrete class implementations, so just don't think about
 * this too hard and you'll be fine.
 */
public abstract class MultiP2PTunnel<T extends MultiP2PTunnel<T, L, P>, L extends MultiP2PTunnel<T, L, P>.Logic, P extends MultiP2PTunnel.Part<T, L, P>> {
  public abstract L createLogic(P part);

  /**
   * Save any data associated with the part to the given tag
   * 
   * @param part
   * @return data to be associated with the part, null if nothing
   */
  public CompoundTag saveNodeData(P part) {
    if (this.customName != null) {
      CompoundTag data = new CompoundTag();
      data
        .putString("customName", Component.Serializer.toJson(this.customName, part.getLevel().registryAccess()));
      return data;
    } else
      return null;
  }

  protected final ReferenceSet<L> inputs = new ReferenceOpenHashSet<>();
  protected final ReferenceSet<L> outputs = new ReferenceOpenHashSet<>();

  private final short freq;
  protected final IGrid grid;

  private Component customName;
  private boolean persistentDataChanged;

  protected void markPersistentDataChanged() { persistentDataChanged = true; }

  /** Shared state changes affect every endpoint's persisted share; coalesce per tick. */
  public void flushPersistentDataChanges() {
    if (!persistentDataChanged) return;
    persistentDataChanged = false;
    for (var logic : inputs) logic.part.getHost().markForSave();
    for (var logic : outputs) logic.part.getHost().markForSave();
  }

  public MultiP2PTunnel(short freq, IGrid grid) {
    this.freq = freq;
    this.grid = grid;
  }

  /**
   * Adds a {@link MultiP2PTunnelPart} to this {@link MultiP2PTunnel},
   * input/ouput is automatically decided
   * 
   * @param tag
   *
   * @return logic associated with this part
   */
  public L addTunnel(P part) {
    L logic = this.createLogic(part);
    if (part.isOutput()) {
      outputs.add(logic);
    } else {
      if (part.hasCustomName()) {
        this.customName = part.getCustomName();
      }
      inputs.add(logic);
    }
    markPersistentDataChanged();
    this.updateTunnels(true, false);
    this.updateTunnels(false, false);
    return logic;
  }

  public L addTunnel(P part, Tag savedData) {
    if (savedData instanceof CompoundTag data && data.contains("customName")) {
      this.customName = Component.Serializer.fromJson(data.getString("customName"), part.getLevel().registryAccess());
    }
    return addTunnel(part);
  }

  /**
   * Removes a {@link MultiP2PTunnelPart} from this {@link MultiP2PTunnel},
   * input/output is automatically decided
   *
   * @return if the part was already in the tunnel
   */
  public boolean removeTunnel(P part) {
    var logic = part.getAttachedLogic();
    boolean removed = logic.isPresent() && (inputs.remove(logic.get()) | outputs.remove(logic.get()));
    part.setLogic(null);
    markPersistentDataChanged();
    updateTunnels(true, false);
    updateTunnels(false, false);
    part.getBlockEntity().invalidateCapabilities();
    return removed;
  }

  protected java.math.BigInteger energyShare(P part, java.math.BigInteger energy) {
    int count = inputs.size() + outputs.size();
    if (count == 0) return java.math.BigInteger.ZERO;
    var split = energy.divideAndRemainder(java.math.BigInteger.valueOf(count));
    int index = 0;
    for (var logic : inputs) {
      if (logic.part == part) return split[0].add(index < split[1].intValue() ? java.math.BigInteger.ONE : java.math.BigInteger.ZERO);
      index++;
    }
    for (var logic : outputs) {
      if (logic.part == part) return split[0].add(index < split[1].intValue() ? java.math.BigInteger.ONE : java.math.BigInteger.ZERO);
      index++;
    }
    return java.math.BigInteger.ZERO;
  }

  protected static void saveEnergy(CompoundTag data, String key, java.math.BigInteger value) {
    data.putLong(key, value.min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValueExact());
    if (value.bitLength() > 63) data.putString(key + "Exact", value.toString());
  }

  protected static java.math.BigInteger readEnergy(CompoundTag data, String key) {
    return data.contains(key + "Exact", Tag.TAG_STRING)
        ? new java.math.BigInteger(data.getString(key + "Exact")).max(java.math.BigInteger.ZERO)
        : java.math.BigInteger.valueOf(Math.max(0, data.getLong(key)));
  }

  protected java.util.List<P> endpointSnapshot() {
    return java.util.stream.Stream.concat(inputs.stream(), outputs.stream()).map(logic -> logic.part).toList();
  }

  /** A receiver may synchronously remove/rebind endpoints (GT overvoltage explosions do).
   * Reserved energy must be refunded to a surviving owner, not to an abandoned grid. */
  protected void refundEnergy(java.math.BigInteger refund, java.util.List<P> previousEndpoints,
      java.util.function.BiConsumer<T, java.math.BigInteger> credit) {
    if (refund.signum() == 0) return;
    if (!inputs.isEmpty() || !outputs.isEmpty()) {
      credit.accept((T) this, refund);
      return;
    }
    for (var part : previousEndpoints) {
      var attached = part.getAttachedLogic();
      if (attached.isPresent()) {
        credit.accept(attached.get().ownerTunnel(), refund);
        return;
      }
    }
    // Destruction of every endpoint destroys the buffer, just as destroying an energy cell.
  }

  public Set<L> getInputs() { return this.inputs; }

  public Set<L> getOutputs() { return this.outputs; }

  public boolean hasCustomName() {
    return this.customName != null;
  }

  public Component getCustomName() { return this.customName; }

  protected void updateTunnels(boolean updateOutputs, boolean configChange) {
    if (updateOutputs) {
      for (L logic : this.outputs) {
        if (configChange) {
          logic.onTunnelConfigChange();
        }
        logic.onTunnelNetworkChange();
      }
    } else {
      for (L logic : this.inputs) {
        if (configChange) {
          logic.onTunnelConfigChange();
        }
        logic.onTunnelNetworkChange();
      }
    }
  }

  public void updateTunnel(L part, boolean settingOutput, short freq) {
    if (this.freq == freq) {
      if (settingOutput) {
        this.outputs.add(part);
        this.inputs.remove(part);
      } else {
        this.outputs.remove(part);
        this.inputs.add(part);
      }
    } else {
      this.outputs.remove(part);
      this.inputs.remove(part);
    }
  }

  protected void deductEnergyCost(double energyTransported,
    PowerUnit typeTransported) {
    var costFactor = AEConfig.instance().getP2PTunnelEnergyTax();
    if (costFactor <= 0) {
      return;
    }

    var tax = typeTransported
      .convertTo(PowerUnit.AE, energyTransported * costFactor);
    this.grid
      .getEnergyService()
      .extractAEPower(tax, Actionable.MODULATE, PowerMultiplier.CONFIG);
  }

  protected void deductTransportCost(long amountTransported,
    AEKeyType typeTransported) {
    var costFactor = AEConfig.instance().getP2PTunnelTransportTax();
    if (costFactor <= 0) {
      return;
    }

    double operations = amountTransported
      / (double) typeTransported.getAmountPerOperation();
    double tax = operations * costFactor;
    this.grid
      .getEnergyService()
      .extractAEPower(tax, Actionable.MODULATE, PowerMultiplier.CONFIG);
  }

  public abstract class Logic {
    protected final P part;

    public Logic(P part) {
      this.part = part;
    }

    protected P getPart() {return this.part;}
    public T ownerTunnel() { return (T) MultiP2PTunnel.this; }
    protected void onTunnelConfigChange() {}

    protected void onTunnelNetworkChange() {}
  }

  public static abstract class Part<T extends MultiP2PTunnel<T, L, P>, L extends MultiP2PTunnel<T, L, P>.Logic, P extends Part<T, L, P>>
    extends AEBasePart {
    private static final String CONFIG_NBT_TYPE = "p2pType";
    private static final String CONFIG_NBT_FREQ = "p2pFreq";

    private boolean output;
    private short freq;
    private Optional<L> logic = Optional.empty();

    public Part(IPartItem<?> partItem) {
      super(partItem);
      this.getMainNode().setIdlePowerUsage(this.getPowerDrainPerTick());
      this.getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    abstract public T createTunnel(short freq);

    abstract public Class<T> getTunnelClass();

    public Optional<L> getLogic() {
      return this.getFrequency() != 0 && this.isActive() ? this.logic : Optional.empty(); }

    protected Optional<L> getAttachedLogic() { return this.logic; }

    protected final L setLogic(L logic) {
      this.logic = Optional.ofNullable(logic);
      return logic;
    }

    protected float getPowerDrainPerTick() { return 1.0f; }

    public T getTunnel() {
      if (this.getMainNode().isOnline()) {
        var grid = getMainNode().getGrid();
        if (grid != null) {
          return (T) grid.getService(MultiP2PService.class).getTunnel(this);
        }
      }

      return null;
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
      bch.addBox(5, 5, 12, 11, 11, 13);
      bch.addBox(3, 3, 13, 13, 13, 14);
      bch.addBox(2, 2, 14, 14, 14, 16);
    }

    @Override
    public void readFromNBT(CompoundTag data, net.minecraft.core.HolderLookup.Provider registries) {
      super.readFromNBT(data, registries);
      this.setOutput(data.getBoolean("output"));
      this.freq = data.getShort("freq");
    }

    @Override
    public void writeToNBT(CompoundTag data, net.minecraft.core.HolderLookup.Provider registries) {
      super.writeToNBT(data, registries);
      data.putBoolean("output", this.isOutput());
      data.putShort("freq", this.getFrequency());
    }

    @Override
    public boolean readFromStream(RegistryFriendlyByteBuf data) {
      final boolean c = super.readFromStream(data);
      final short oldf = this.freq;
      this.freq = data.readShort();
      return c || oldf != this.freq;
    }

    @Override
    public void writeToStream(RegistryFriendlyByteBuf data) {
      super.writeToStream(data);
      data.writeShort(this.getFrequency());
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
      return 1;
    }

    @Override
    public boolean useStandardMemoryCard() {
      return false;
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, Player player, InteractionHand hand,
      Vec3 pos) {
      if (appeng.util.InteractionUtil.isInAlternateUseMode(player)) return onPartShiftActivate(player, hand, pos);
      if (isClientSide()) {
        return true;
      }

      if (hand == InteractionHand.OFF_HAND) {
        return false;
      }

      ItemStack is;
      boolean settingOutput;
      if (player.getMainHandItem().isEmpty()) {
        is = player.getOffhandItem();
        settingOutput = false;
      } else {
        is = player.getMainHandItem();
        settingOutput = true;
      }

      // Prefer restoring from memory card
      if (!is.isEmpty() && is.getItem() instanceof IMemoryCard mc) {
        var configData = is.getComponents();

        // Change the actual tunnel type and import settings when the encoded
        // type is a
        // P2P
        IPartItem<?> partItem = configData.get(AEComponents.EXPORTED_P2P_TYPE) instanceof IPartItem<?> item ? item : null;
        if (partItem != null && MultiP2PTunnel.Part.class
          .isAssignableFrom(partItem.getPartClass())) {
          IPart newBus = this;
          if (newBus.getPartItem() != partItem) {
            newBus = this
              .getHost()
              .replacePart(partItem, this.getSide(), player, hand);
          }
          if (newBus instanceof Part<?, ?, ?> newTunnel) {
            newTunnel
              .importSettings(SettingsFrom.MEMORY_CARD, configData, player,
                settingOutput);
          }
          mc.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
          return true;
        }
        mc.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
        return false;
      } else
      // Attunement via held item replaces the tunnel part with the desired
      // target
      // part type
      if (P2PTunnelAttunement
        .getTunnelPartByTriggerItem(is)
        .getItem() instanceof IPartItem singleType) {
        IPartItem<? extends Part<?, ?, ?>> newType = MultiP2PTunnelAttunement
          .getMultiPartBySinglePart(singleType);
        if (newType != null && newType != getPartItem()) {
          boolean oldOutput = isOutput();
          short myFreq = getFrequency();

          // If we were able to replace the tunnel part, copy over
          // frequency/output state
          Part<?, ?, ?> part = getHost()
            .replacePart(newType, getSide(), player, hand);
          if (part == null) return false;
          part.setFrequency(myFreq);
          part.setOutput(oldOutput);

          // setting the freq/output automagically sets up the tunnel
          Platform
            .notifyBlocksOfNeighbors(getLevel(),
              getBlockEntity().getBlockPos());
          return true;
        }
      }

      return false;
    }

    public boolean onPartShiftActivate(Player player, InteractionHand hand,
      Vec3 pos) {
      final ItemStack is = player.getInventory().getSelected();
      if (!is.isEmpty() && is.getItem() instanceof IMemoryCard mc) {
        if (isClientSide()) {
          return true;
        }

        final short storedFrequency = is.getOrDefault(AEComponents.EXPORTED_P2P_FREQUENCY, (short) 0);

        short newFreq = this.getFrequency();

        final boolean needsNewFrequency = newFreq == 0 || storedFrequency == newFreq;

        var grid = getMainNode().getGrid();
        if (grid != null) {
          MultiP2PService p2p = grid.getService(MultiP2PService.class);
          if (needsNewFrequency) {
            newFreq = p2p.newFrequency(this.getTunnelClass());
            this.setFrequency(newFreq);
            this.setOutput(false);
          }
        }

        this.getLogic().ifPresent(logic -> logic.onTunnelConfigChange());

        var type = getPartItem().asItem().getDescriptionId();

        MemoryCardItem.clearCard(is);
        is.applyComponents(exportSettings(SettingsFrom.MEMORY_CARD));
        if (needsNewFrequency) {
          mc.notifyUser(player, MemoryCardMessages.SETTINGS_RESET);
        } else {
          mc.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
        }
        return true;
      }
      return false;
    }

    public void importSettings(SettingsFrom mode, DataComponentMap input,
      @Nullable Player player, boolean settingOutput) {

      super.importSettings(mode, input, player);

      if (input.has(AEComponents.EXPORTED_P2P_FREQUENCY)) {
        short freq = input.get(AEComponents.EXPORTED_P2P_FREQUENCY);

        // Only make this an output, if it's not already on the frequency.
        // Otherwise, the tunnel input may be made unusable by accidentally
        // loading it
        // with its own settings
        if (freq != this.freq || !settingOutput) {
          setOutput(settingOutput);
          setFrequency(freq);
          this.getLogic().ifPresent(logic -> logic.onTunnelNetworkChange());
        }
      }
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
      importSettings(mode, input, player, true);
    }

    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder) {
      super.exportSettings(mode, builder);
      if (mode == SettingsFrom.MEMORY_CARD) {
        builder.set(AEComponents.EXPORTED_P2P_TYPE, getPartItem().asItem());
        if (freq != 0) {
          builder.set(AEComponents.EXPORTED_P2P_FREQUENCY, freq);
          var colors = Platform.p2p().toColors(freq);
          builder.set(AEComponents.MEMORY_CARD_COLORS, new MemoryCardColors(
              colors[0], colors[0], colors[1], colors[1], colors[2], colors[2], colors[3], colors[3]));
        }
      }
    }

    public short getFrequency() { return this.freq; }

    public void setFrequency(short freq) {
      if (this.freq != freq) {
        var tunnel = this.getTunnel();
        CompoundTag moved = tunnel == null ? null : tunnel.saveNodeData((P) this);
        if (tunnel != null) tunnel.removeTunnel((P) this);
        this.freq = freq;
        tunnel = this.getTunnel();
        if (tunnel != null)
          tunnel.addTunnel((P) this, moved);
        this.getBlockEntity().invalidateCapabilities();
        this.getHost().markForSave();
        this.getHost().markForUpdate();
      }
    }

    public boolean isOutput() { return this.output; }

    void setOutput(boolean output) {
      if (this.output != output) {
        T tunnel = this.getTunnel();
        CompoundTag moved = tunnel == null ? null : tunnel.saveNodeData((P) this);
        if (tunnel != null) tunnel.removeTunnel((P) this);
        this.output = output;
        tunnel = this.getTunnel();
        if (tunnel != null)
          tunnel.addTunnel((P) this, moved);
        this.getBlockEntity().invalidateCapabilities();
        this.getHost().markForSave();
      }
    }

    @Override
    public ModelData getModelData() {
      long ret = Short.toUnsignedLong(this.getFrequency());

      if (this.isActive() && this.isPowered()) {
        ret |= 0x10000L;
      }

      return ModelData
        .builder()
        .with(P2PTunnelFrequencyModelData.FREQUENCY, ret)
        .build();
    }
  }
}
