package stone.mae2.gametests;

import appeng.api.parts.IPartHost;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import stone.mae2.MAE2;
import stone.mae2.bootstrap.MAE2Items;
import stone.mae2.me.service.MultiP2PService;
import stone.mae2.parts.p2p.multi.MultiP2PTunnel;
import java.io.StringWriter;

@GameTestHolder("mae2")
@PrefixGameTestTemplate(false)
public class LifecycleRegression {
    @GameTest(template="empty", timeoutTicks=300)
    public static void thousandRebindsReleaseObsoleteNetworksAndTickers(GameTestHelper h) {
        TransferRegression.network(h, MAE2Items.FE_MULTI_P2P_TUNNEL.get());
        h.startSequence().thenWaitUntil(() -> TransferRegression.ready(h)).thenExecute(() -> {
            var part = (MultiP2PTunnel.Part<?, ?, ?>) ((IPartHost) h.getBlockEntity(new BlockPos(1, 1, 2))).getPart(Direction.NORTH);
            long start = System.nanoTime();
            for (int i = 0; i < 1000; i++) part.setFrequency((short) (10000 + i));
            MAE2.LOGGER.info("Regression: 1000 frequency rebinds took {} ms", (System.nanoTime() - start) / 1e6);
        }).thenIdle(2).thenExecute(() -> {
            var part = ((IPartHost) h.getBlockEntity(new BlockPos(1, 1, 2))).getPart(Direction.NORTH);
            var service = part.getGridNode().getGrid().getService(MultiP2PService.class);
            var output = new StringWriter();
            try (var writer = new JsonWriter(output)) { service.debugDump(writer, h.getLevel().registryAccess()); }
            catch (java.io.IOException e) { throw new RuntimeException(e); }
            var result = JsonParser.parseString(output.toString()).getAsJsonObject();
            h.assertTrue(result.get("tunnels").getAsInt() == 2, "Obsolete frequencies leaked");
            h.assertTrue(result.get("scheduledTickers").getAsInt() == 2, "Obsolete ticking tunnels leaked");
            h.assertTrue(result.get("inputs").getAsInt() == 2 && result.get("outputs").getAsInt() == 1, "Rebind duplicated or removed endpoints");
        }).thenSucceed();
    }
    @GameTest(template="empty", timeoutTicks=300)
    public static void feBufferSurvivesRealGridSplitAndMerge(GameTestHelper h) {
        TransferRegression.network(h, MAE2Items.FE_MULTI_P2P_TUNNEL.get());
        h.setBlock(1, 0, 2, appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(3, 0, 2, appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        var inputPos = new BlockPos(1, 1, 2);
        var middlePos = new BlockPos(2, 1, 2);
        var outputPos = new BlockPos(3, 1, 2);
        h.startSequence().thenWaitUntil(() -> TransferRegression.ready(h)).thenExecute(() -> {
            var input = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                    h.absolutePos(inputPos), Direction.NORTH);
            h.assertTrue(input != null && input.receiveEnergy(503, false) == 503, "FE initialization failed");
            var host = (IPartHost) h.getBlockEntity(middlePos);
            host.removePart(host.getPart(null));
        }).thenWaitUntil(() -> {
            var input = ((IPartHost) h.getBlockEntity(inputPos)).getPart(Direction.NORTH);
            var output = ((IPartHost) h.getBlockEntity(outputPos)).getPart(Direction.NORTH);
            h.assertTrue(input.getGridNode().getGrid() != output.getGridNode().getGrid(), "Cable removal did not split the grid");
        }).thenExecute(() -> {
            long sum = 0;
            for (int x = 1; x <= 3; x++) {
                var saved = h.getBlockEntity(new BlockPos(x, 1, 2)).saveWithFullMetadata(h.getLevel().registryAccess());
                sum += saved.getCompound("north").getCompound("gn").getCompound("mae2:multi_p2p").getLong("energy");
            }
            h.assertTrue(sum == 503, "Grid split lost or duplicated shared energy");
            ((IPartHost) h.getBlockEntity(middlePos)).addPart(appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT), null, null);
        }).thenWaitUntil(() -> {
            TransferRegression.ready(h);
            var input = ((IPartHost) h.getBlockEntity(inputPos)).getPart(Direction.NORTH);
            var output = ((IPartHost) h.getBlockEntity(outputPos)).getPart(Direction.NORTH);
            h.assertTrue(input.getGridNode().getGrid() == output.getGridNode().getGrid(), "Cable restoration did not merge the grid");
            var cap = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                    h.absolutePos(outputPos), Direction.NORTH);
            h.assertTrue(cap != null && cap.extractEnergy(1000, true) == 503 && cap.extractEnergy(1000, false) == 503,
                    "Grid merge lost or duplicated buffered FE");
        }).thenSucceed();
    }
    @GameTest(template="empty", timeoutTicks=300)
    public static void maximumEuBuffersMergeAndReloadWithoutOverflow(GameTestHelper h) {
        TransferRegression.network(h, stone.mae2.integration.GregTechIntegration.EU_MULTI_P2P_TUNNEL.get());
        var positions = java.util.List.of(new BlockPos(1, 1, 2), new BlockPos(2, 1, 2), new BlockPos(3, 1, 2));
        h.startSequence().thenWaitUntil(() -> TransferRegression.ready(h)).thenExecute(() -> {
            var first = (MultiP2PTunnel.Part<?, ?, ?>) ((IPartHost) h.getBlockEntity(positions.getFirst())).getPart(Direction.NORTH);
            first.setFrequency((short) 7212);
            for (int x : new int[]{1, 2}) {
                var input = h.getLevel().getCapability(com.gregtechceu.gtceu.api.capability.GTCapability.CAPABILITY_ENERGY_CONTAINER,
                    h.absolutePos(new BlockPos(x, 1, 2)), Direction.NORTH);
                h.assertTrue(input != null && input.acceptEnergyFromNetwork(Direction.NORTH, Long.MAX_VALUE, 1) == 1, "Maximum packet initialization failed");
            }
            first.setFrequency((short) 7211);
            assertDoubleMaximumEnergy(h, positions);
            var previous = positions.stream().map(p -> (net.minecraft.world.level.block.entity.BlockEntity) h.getBlockEntity(p)).toList();
            var saved = previous.stream().map(be -> be.saveWithFullMetadata(h.getLevel().registryAccess())).toList();
            for (var be : previous) h.getLevel().removeBlockEntity(be.getBlockPos());
            for (int n = 0; n < previous.size(); n++) {
                var be = previous.get(n);
                var replacement = be.getType().create(be.getBlockPos(), be.getBlockState());
                replacement.loadWithComponents(saved.get(n), h.getLevel().registryAccess());
                h.getLevel().setBlockEntity(replacement);
                replacement.onLoad();
            }
        }).thenWaitUntil(() -> TransferRegression.ready(h)).thenExecute(() -> assertDoubleMaximumEnergy(h, positions)).thenSucceed();
    }

    private static void assertDoubleMaximumEnergy(GameTestHelper h, java.util.List<BlockPos> positions) {
        var total = java.math.BigInteger.ZERO;
        for (var position : positions) {
            var saved = h.getBlockEntity(position).saveWithFullMetadata(h.getLevel().registryAccess());
            var data = saved.getCompound("north").getCompound("gn").getCompound("mae2:multi_p2p");
            total = total.add(data.contains("energyExact") ? new java.math.BigInteger(data.getString("energyExact")) : java.math.BigInteger.valueOf(data.getLong("energy")));
        }
        h.assertTrue(total.equals(java.math.BigInteger.valueOf(Long.MAX_VALUE).multiply(java.math.BigInteger.TWO)),
            "Merging/reloading valid buffers overflowed or lost EU: " + total);
    }
}
