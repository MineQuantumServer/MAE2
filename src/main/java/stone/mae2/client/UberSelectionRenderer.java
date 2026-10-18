package stone.mae2.client;
import stone.mae2.item.faulty.*;

import org.joml.Matrix4f;


import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;
import appeng.api.util.AEColor;
import appeng.block.AEBaseEntityBlock;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.bus.api.SubscribeEvent;
import stone.mae2.MAE2;
import stone.mae2.bootstrap.MAE2Config;
import stone.mae2.bootstrap.MAE2Items;
import stone.mae2.util.TransHelper;

public final class UberSelectionRenderer {
  // TODO: move this to main faulty card class so others could utilize it
  @SubscribeEvent
  public static void onRenderLevel(RenderLevelStageEvent event) {
    if (event.getStage() != Stage.AFTER_SOLID_BLOCKS)
      return;
    Minecraft client = Minecraft.getInstance();
    LocalPlayer player = client.player;
    ItemStack main = player.getMainHandItem();
    if (main.isEmpty()) {
      main = player.getOffhandItem();
    }
    if (!main.isEmpty() && main.getItem() == MAE2Items.FAULTY_MEMORY_CARD.get()) {
      FaultyCardMode mode = FaultyCardMode.of(main);
      if (mode instanceof UberMode uber) {
        BlockHitResult hit = UberMode.rayTrace(player.level(), player);
        if (uber.getStart() != null && hit.getType() == HitResult.Type.BLOCK) {
          BlockPos end = hit.getBlockPos();
          // rendering stuff I kinda understand
          PoseStack poses = event.getPoseStack();
          poses.pushPose();
          Vec3 camera = event.getCamera().getPosition();
          poses.translate(-camera.x, -camera.y, -camera.z);
          // this automatically figures out how to fit the entire corner blocks
          // into the bounding box
          BoundingBox box = BoundingBox.fromCorners(end, uber.getStart());
          int color = uber.isVolumeValid(end) ? AEColor.LIGHT_BLUE.mediumVariant : AEColor.RED.mediumVariant;
          float red   = ((color & 0xFF0000) >> 16) / 255f;
          float green = ((color & 0x00FF00) >>  8) / 255f;
          float blue  = ((color & 0x0000FF) >>  0) / 255f;

          // what is a tesselator? idk, but this lets me render stuff
          // feel like this should be bad, but idk. Should only be a problem when
          // rendering the box anyways
          net.minecraft.client.renderer.MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();

          VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
          // inflate by tiny bit to prevent z-fighting
          AABB aabb = AABB.of(box).inflate(0.01);
          LevelRenderer.renderLineBox(poses, consumer, aabb, red, green, blue, 1);
          buffer.endBatch();
          poses.popPose();
        }
      }
    }
  }

}
