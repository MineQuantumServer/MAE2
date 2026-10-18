package stone.mae2.gametests;

import appeng.api.parts.IPartHost;
import appeng.core.definitions.AEParts;
import appeng.util.SettingsFrom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import stone.mae2.bootstrap.MAE2Items;
import stone.mae2.item.faulty.*;
import stone.mae2.parts.p2p.multi.MultiP2PTunnel;

@GameTestHolder("mae2")
@PrefixGameTestTemplate(false)
public class MemoryCardRegression {
    private static BlockPos pos(int x) { return new BlockPos(x, 1, 2); }
    private static MultiP2PTunnel.Part<?, ?, ?> part(GameTestHelper h, int x) {
        return (MultiP2PTunnel.Part<?, ?, ?>) ((IPartHost) h.getBlockEntity(pos(x))).getPart(Direction.NORTH);
    }
    private static UseOnContext context(GameTestHelper h, net.minecraft.world.entity.player.Player p, int x, Direction side) {
        var absolute = h.absolutePos(pos(x));
        Vec3 hit = Vec3.atCenterOf(absolute).add(side.getStepX() * .5, side.getStepY() * .5, side.getStepZ() * .5);
        return new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, side, absolute, false));
    }
    @GameTest(template="empty", timeoutTicks=200)
    public static void aoeGlobalAndUberPasteRealParts(GameTestHelper h) {
        TransferRegression.network(h, MAE2Items.FE_MULTI_P2P_TUNNEL.get());
        h.startSequence().thenWaitUntil(() -> TransferRegression.ready(h)).thenExecute(() -> {
            var player = FakePlayerFactory.getMinecraft(h.getLevel());
            var card = new ItemStack(MAE2Items.FAULTY_MEMORY_CARD.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, card);
            // Copy through the same native AE2 item interaction used by the client packet.
            player.setShiftKeyDown(true);
            h.assertTrue(part(h, 1).onUseItemOn(card, player, InteractionHand.MAIN_HAND, context(h, player, 1, Direction.NORTH).getClickLocation()), "Copy failed");
            player.setShiftKeyDown(false);
            for (var mode : new FaultyCardMode[]{new AoEPaste(), new GlobalPaste(), new UberMode()}) {
                part(h, 2).setFrequency((short) 7212);
                part(h, 3).setFrequency((short) 7212);
                mode.save(card);
                var item = (FaultyMemoryCardItem) card.getItem();
                item.onItemUseFirst(card, context(h, player, 2, Direction.NORTH));
                if (mode instanceof UberMode) item.onItemUseFirst(card, context(h, player, 3, Direction.NORTH));
                h.assertTrue(part(h, 2).getFrequency() == part(h, 1).getFrequency() && part(h, 2).isOutput(), "First paste target failed: " + mode.getType());
                h.assertTrue(part(h, 3).getFrequency() == part(h, 1).getFrequency() && part(h, 3).isOutput(), "Second paste target failed: " + mode.getType());
                h.assertTrue(!part(h, 1).isOutput(), "Paste overwrote the frequency source");
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }).thenSucceed();
    }
    @GameTest(template="empty", timeoutTicks=200)
    public static void incrementAndDecrementNativePriority(GameTestHelper h) {
        TransferRegression.network(h, MAE2Items.FE_MULTI_P2P_TUNNEL.get());
        var bus = ((IPartHost) h.getBlockEntity(pos(1))).addPart(AEParts.STORAGE_BUS.get(), Direction.WEST, null);
        bus.setPriority(10);
        h.startSequence().thenWaitUntil(() -> TransferRegression.ready(h)).thenExecute(() -> {
            var player = FakePlayerFactory.getMinecraft(h.getLevel());
            var card = new ItemStack(MAE2Items.FAULTY_MEMORY_CARD.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, card);
            player.setShiftKeyDown(true);
            bus.onUseItemOn(card, player, InteractionHand.MAIN_HAND, context(h, player, 1, Direction.WEST).getClickLocation());
            player.setShiftKeyDown(false);
            new Incrementing().save(card); // Original default is decrement.
            var item = (FaultyMemoryCardItem) card.getItem();
            var ctx = context(h, player, 1, Direction.WEST);
            item.onItemUseFirst(card, ctx);
            bus.onUseItemOn(card, player, InteractionHand.MAIN_HAND, ctx.getClickLocation());
            h.assertTrue(bus.getPriority() == 9, "Decrement did not update AE2 priority");
            FaultyCardMode.of(card).onItemUse(h.getLevel(), player, InteractionHand.MAIN_HAND);
            item.onItemUseFirst(card, ctx);
            bus.onUseItemOn(card, player, InteractionHand.MAIN_HAND, ctx.getClickLocation());
            h.assertTrue(bus.getPriority() == 10, "Increment did not update AE2 priority");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }).thenSucceed();
    }
}
