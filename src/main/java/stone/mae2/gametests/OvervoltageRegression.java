package stone.mae2.gametests;

import com.gregtechceu.gtceu.api.capability.GTCapability;
import com.gregtechceu.gtceu.common.data.GTMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import stone.mae2.integration.GregTechIntegration;

@GameTestHolder("mae2")
@PrefixGameTestTemplate(false)
public class OvervoltageRegression {
    @GameTest(template="empty", batch="mae2_overvoltage", timeoutTicks=200)
    public static void ordinaryEuPreservesGtOvervoltage(GameTestHelper h) { run(h, false); }
    @GameTest(template="empty", batch="mae2_overvoltage", timeoutTicks=200)
    public static void multiEuPreservesGtOvervoltage(GameTestHelper h) { run(h, true); }
    private static void run(GameTestHelper h, boolean multi) {
        TransferRegression.network(h, multi ? GregTechIntegration.EU_MULTI_P2P_TUNNEL.get() : GregTechIntegration.EU_P2P_TUNNEL.get());
        var target = new BlockPos(3, 1, 1);
        h.setBlock(target, GTMachines.ELECTRIC_FURNACE[1].getBlock());
        h.startSequence().thenWaitUntil(() -> TransferRegression.ready(h)).thenExecute(() -> {
            var input = h.getLevel().getCapability(GTCapability.CAPABILITY_ENERGY_CONTAINER,
                h.absolutePos(new BlockPos(1, 1, 2)), Direction.NORTH);
            h.assertTrue(input != null && input.acceptEnergyFromNetwork(Direction.NORTH, 128, 1) == 1, "Overvoltage packet was silently changed/rejected");
        }).thenWaitUntil(() -> h.assertTrue(!h.getBlockState(target).is(GTMachines.ELECTRIC_FURNACE[1].getBlock()),
            "Tunnel suppressed specified GT's overvoltage explosion")).thenSucceed();
    }
}
