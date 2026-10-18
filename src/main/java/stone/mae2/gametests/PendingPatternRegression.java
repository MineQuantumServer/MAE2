package stone.mae2.gametests;

import appeng.api.AECapabilities;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import stone.mae2.bootstrap.MAE2Items;
import java.util.List;

@GameTestHolder("mae2")
@PrefixGameTestTemplate(false)
public class PendingPatternRegression {
    @GameTest(template="empty", timeoutTicks=300)
    public static void ordinaryPendingMaterialsReload(GameTestHelper h) { run(h, false); }
    @GameTest(template="empty", timeoutTicks=300)
    public static void multiPendingMaterialsReload(GameTestHelper h) { run(h, true); }
    private static void run(GameTestHelper h, boolean multi) {
        TransferRegression.network(h, multi ? MAE2Items.PATTERN_MULTI_P2P_TUNNEL.get() : MAE2Items.PATTERN_P2P_TUNNEL.get());
        var sinkPos = new BlockPos(3, 1, 1);
        var outputPos = new BlockPos(3, 1, 2);
        h.setBlock(sinkPos, Blocks.BARREL);
        h.startSequence().thenWaitUntil(() -> TransferRegression.ready(h)).thenExecute(() -> {
            var sink = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(sinkPos), null);
            sink.insertItem(0, new ItemStack(Items.IRON_INGOT, 60), false);
            for (int i = 1; i < sink.getSlots(); i++) sink.insertItem(i, new ItemStack(Items.DIRT, 64), false);
            var patternItem = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 8)),
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)));
            var details = PatternDetailsHelper.decodePattern(patternItem, h.getLevel());
            var ingredients = new KeyCounter();
            ingredients.add(AEItemKey.of(Items.IRON_INGOT), 8);
            var tunnel = h.getLevel().getCapability(AECapabilities.CRAFTING_MACHINE, h.absolutePos(new BlockPos(1, 1, 2)), Direction.NORTH);
            h.assertTrue(tunnel != null && tunnel.pushPattern(details, new KeyCounter[]{ingredients}, Direction.NORTH), "Complete processing pattern was not claimed");
            h.assertTrue(sink.getStackInSlot(0).getCount() == 64, "Initial partial delivery failed");
            var previous = h.getBlockEntity(outputPos);
            var saved = previous.saveWithFullMetadata(h.getLevel().registryAccess());
            var pending = saved.getCompound("north").getList("sendList", net.minecraft.nbt.Tag.TAG_COMPOUND);
            h.assertTrue(pending.size() == 1 && GenericStack.readTag(h.getLevel().registryAccess(), pending.getCompound(0)).amount() == 4, "Pending debt was not persisted");
            sink.extractItem(0, 4, false);
            // Detach and recreate the BE without block destruction/drops, as during a chunk reload.
            var replacement = previous.getType().create(previous.getBlockPos(), previous.getBlockState());
            h.getLevel().removeBlockEntity(previous.getBlockPos());
            replacement.loadWithComponents(saved, h.getLevel().registryAccess());
            h.getLevel().setBlockEntity(replacement);
            replacement.onLoad();
        }).thenWaitUntil(() -> {
            var sink = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(sinkPos), null);
            h.assertTrue(sink.getStackInSlot(0).getCount() == 64, "Restored pending materials were not delivered");
            var saved = h.getBlockEntity(outputPos).saveWithFullMetadata(h.getLevel().registryAccess());
            h.assertTrue(saved.getCompound("north").getList("sendList", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty(), "Delivered materials remained queued");
        }).thenSucceed();
    }
}
