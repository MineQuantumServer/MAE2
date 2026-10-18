package stone.mae2.gametests;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.api.ids.AEComponents;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import stone.mae2.MAE2;
import stone.mae2.bootstrap.MAE2Blocks;
import stone.mae2.bootstrap.MAE2Items;
import stone.mae2.item.faulty.*;

@GameTestHolder(MAE2.MODID)
@PrefixGameTestTemplate(false)
public class Regression {
    private static final BlockPos CORE = new BlockPos(0, 1, 0);
    private static void accelerator(GameTestHelper helper, Block block, int threads) {
        helper.setBlock(CORE, AEBlocks.CRAFTING_STORAGE_1K.block());
        helper.setBlock(1, 1, 0, block);
        helper.succeedWhen(() -> assertThreads(helper, threads));
    }
    private static void assertThreads(GameTestHelper helper, int expected) {
        var entity = (CraftingBlockEntity) helper.getBlockEntity(CORE);
        helper.assertTrue(entity.getCluster() != null, "CPU did not form");
        helper.assertTrue(entity.getCluster().getCoProcessors() == expected, "Incorrect actual co-processor count: expected " + expected);
    }
    @GameTest(template="empty") public static void accelerator4(GameTestHelper h) { accelerator(h, MAE2Blocks.ACCELERATOR_4x.get(), 4); }
    @GameTest(template="empty") public static void accelerator16(GameTestHelper h) { accelerator(h, MAE2Blocks.ACCELERATOR_16x.get(), 16); }
    @GameTest(template="empty") public static void accelerator64(GameTestHelper h) { accelerator(h, MAE2Blocks.ACCELERATOR_64x.get(), 64); }
    @GameTest(template="empty") public static void accelerator256(GameTestHelper h) { accelerator(h, MAE2Blocks.ACCELERATOR_256x.get(), 256); }
    @GameTest(template="empty", timeoutTicks=200)
    public static void acceleratorMixedRebuild(GameTestHelper h) {
        h.setBlock(CORE, AEBlocks.CRAFTING_STORAGE_1K.block());
        Block[] blocks = {MAE2Blocks.ACCELERATOR_4x.get(), MAE2Blocks.ACCELERATOR_16x.get(), MAE2Blocks.ACCELERATOR_64x.get(), MAE2Blocks.ACCELERATOR_256x.get(), AEBlocks.CRAFTING_ACCELERATOR.block()};
        for (int i = 0; i < blocks.length; i++) h.setBlock(i + 1, 1, 0, blocks[i]);
        h.startSequence().thenWaitUntil(() -> assertThreads(h, 341))
            .thenExecute(() -> h.setBlock(5, 1, 0, Blocks.AIR))
            .thenWaitUntil(() -> assertThreads(h, 340))
            .thenExecute(() -> h.setBlock(5, 1, 0, AEBlocks.CRAFTING_ACCELERATOR.block()))
            .thenWaitUntil(() -> assertThreads(h, 341)).thenSucceed();
    }
    @GameTest(template="empty")
    public static void faultyComponentsDiskAndNetwork(GameTestHelper h) {
        for (FaultyCardMode mode : new FaultyCardMode[]{new AoEPaste(), new GlobalPaste(), new Incrementing(), new UberMode()}) {
            ItemStack stack = new ItemStack(MAE2Items.FAULTY_MEMORY_CARD.get());
            mode.save(stack);
            stack.set(AEComponents.EXPORTED_P2P_FREQUENCY, (short) 65432);
            var restored = ItemStack.parseOptional(h.getLevel().registryAccess(), (net.minecraft.nbt.CompoundTag) stack.saveOptional(h.getLevel().registryAccess()));
            h.assertTrue(FaultyCardMode.of(restored).getType().equals(mode.getType()), "Lost mode on disk roundtrip");
            h.assertTrue(restored.get(AEComponents.EXPORTED_P2P_FREQUENCY).equals((short) 65432), "Lost frequency");
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
            try {
                ItemStack.STREAM_CODEC.encode(buf, stack);
                var synced = ItemStack.STREAM_CODEC.decode(buf);
                h.assertTrue(ItemStack.isSameItemSameComponents(stack, synced), "Lost card state on network roundtrip");
            } finally { buf.release(); }
        }
        h.succeed();
    }
    @GameTest(template="empty", timeoutTicks=200)
    public static void acceleratorSavedClusterReforms(GameTestHelper h) {
        h.setBlock(CORE, AEBlocks.CRAFTING_STORAGE_1K.block());
        h.setBlock(1, 1, 0, MAE2Blocks.ACCELERATOR_256x.get());
        h.startSequence().thenWaitUntil(() -> assertThreads(h, 256)).thenExecute(() -> {
            var cpu = h.getBlockEntity(CORE).saveWithFullMetadata(h.getLevel().registryAccess());
            var accelerator = h.getBlockEntity(new BlockPos(1, 1, 0)).saveWithFullMetadata(h.getLevel().registryAccess());
            h.setBlock(1, 1, 0, Blocks.AIR);
            h.setBlock(CORE, Blocks.AIR);
            h.setBlock(CORE, AEBlocks.CRAFTING_STORAGE_1K.block());
            h.setBlock(1, 1, 0, MAE2Blocks.ACCELERATOR_256x.get());
            h.getBlockEntity(CORE).loadWithComponents(cpu, h.getLevel().registryAccess());
            h.getBlockEntity(new BlockPos(1, 1, 0)).loadWithComponents(accelerator, h.getLevel().registryAccess());
        }).thenWaitUntil(() -> assertThreads(h, 256)).thenSucceed();
    }

}
