package stone.mae2.item.faulty;



import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;
import appeng.api.util.AEColor;
import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import stone.mae2.MAE2;
import stone.mae2.bootstrap.MAE2Config;
import stone.mae2.bootstrap.MAE2Items;
import stone.mae2.util.TransHelper;

public class UberMode extends FaultyCardMode {
  private static final String START_POS = "start";
  private BlockPos start;
	@Override
	public ResourceLocation getType() {
    return MAE2.toKey("uber_mode");
	}
    
  @Override
  public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
    if (this.start == null) {
      this.start = context.getClickedPos();
      this.save(stack);
      context.getPlayer().displayClientMessage(Component.translatable(TransHelper.GUI.toKey("faulty", "uber", "start")), true);
      return InteractionResult.CONSUME;
    } else {
      BlockPos end = context.getClickedPos();
      Level level = context.getLevel();
      BlockEntity be = level.getBlockEntity(context.getClickedPos());
      Direction side = context.getClickedFace();
      Player player = context.getPlayer();
      if (be instanceof IPartHost originalHost) {
        SelectedPart selectedPart = originalHost.selectPartWorld(context.getClickLocation());
        side = selectedPart.side;
      }
      if (side == null) return InteractionResult.PASS;
      if (!isVolumeValid(end)) {
        player.displayClientMessage(TransHelper.GUI.translatable("faulty.uber.failed"), true);
      } else {
        boolean failed = false;
        InteractionHand hand = context.getHand();
        Vec3 clickLocation = context.getClickLocation();
        for (BlockPos pos : BlockPos.betweenClosed(start, end)) {
          if (!level.hasChunkAt(pos)) continue;
          BlockState state = level.getBlockState(pos);
          if (level.getBlockEntity(pos) instanceof IPartHost aoePartHost) {
              IPart part = aoePartHost.getPart(side);
              if (part != null) {
                // no idea what the Vec pos argument does here, doesn't seem used in any implementation
                failed |= !part.onUseItemOn(stack, player, hand, clickLocation);
              }
          } else if (state.getBlock() instanceof AEBaseEntityBlock baseBlock) {
            state.useItemOn(stack, level, player, hand, new BlockHitResult(clickLocation, side, pos, false));
          }
        }

        if (failed) {
          context.getPlayer().displayClientMessage(TransHelper.GUI.translatable("faulty.multi.failed"), true);
        } else {
          context.getPlayer().displayClientMessage(TransHelper.GUI.translatable("faulty.multi.succeeded"), true);
        }
      }
      this.start = null;
      this.save(stack);
      return InteractionResult.CONSUME;
    }
  }

  @Override
  public InteractionResult onItemSecondaryUseFirst(ItemStack stack, UseOnContext context) {
    this.start = null;
    this.save(stack);
    return InteractionResult.PASS;
  }

  public boolean isVolumeValid(BlockPos end) {
    if (start == null) return false;
    return stone.mae2.util.TransferMath.volumeWithin(start.getX(), start.getY(), start.getZ(),
        end.getX(), end.getY(), end.getZ(), MAE2.CONFIG.faulty().maxUberVolume());
  }

  @Override
  protected FaultyCardMode load(CompoundTag tag) {
    if (tag.contains(START_POS)) {
      this.start = BlockPos.of(tag.getLong(START_POS));
    }
    return this;
  }
    
  @Override
  public CompoundTag save(CompoundTag tag) {
    CompoundTag data = super.save(tag);
    if (this.start != null) {
      data.putLong(START_POS, this.start.asLong());
    }
    return data;
  }

	@Override
	protected Component getName() {
    return Component.translatable(TransHelper.GUI.toKey("faulty", "uber"));
	}

	@Override
	public int getTintColor() {
    return AEColor.LIGHT_BLUE.mediumVariant;
	}

  public BlockPos getStart() { return start; }

  // TODO move this to a shared utils class
  public static BlockHitResult rayTrace(Level level, Player player) {
    return rayTrace(level, player, player.blockInteractionRange());
  }
  // stolen with permission from Spatial Tools Compatiable
  public static BlockHitResult rayTrace(Level level, Player player, double maxDistance) {
    Vec3 eye = player.getEyePosition(1.0F);
    Vec3 look = player.getViewVector(1.0F);
    Vec3 end = eye.add(look.x * maxDistance, look.y * maxDistance, look.z * maxDistance);

    ClipContext context = new ClipContext(
                                          eye,
                                          end,
                                          ClipContext.Block.OUTLINE,
                                          ClipContext.Fluid.NONE,
                                          player);

    HitResult result = level.clip(context);
    if (result instanceof BlockHitResult blockHit && result.getType() == HitResult.Type.BLOCK) {
      return blockHit;
    }

    return BlockHitResult.miss(end, Direction.getNearest(look.x, look.y, look.z), BlockPos.containing(end));
  }
}
