package stone.mae2.gametests;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.parts.IPartHost;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.core.AEConfig;
import appeng.core.definitions.AEBlocks;
import com.gregtechceu.gtceu.api.capability.GTCapability;
import com.gregtechceu.gtceu.api.capability.compat.FeCompat;
import com.gregtechceu.gtceu.common.data.GTMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import stone.mae2.MAE2;
import stone.mae2.bootstrap.MAE2Config;
import stone.mae2.integration.GregTechIntegration;
import stone.mae2.me.service.MultiP2PService;

@GameTestHolder("mae2")
@PrefixGameTestTemplate(false)
public class TaxRegression {
    private static void finiteNetwork(GameTestHelper h, boolean multi) {
        TransferRegression.network(h, multi ? GregTechIntegration.EU_MULTI_P2P_TUNNEL.get() : GregTechIntegration.EU_P2P_TUNNEL.get());
        h.setBlock(2, 2, 2, AEBlocks.ENERGY_CELL.block());
        ((EnergyCellBlockEntity) h.getBlockEntity(new BlockPos(2, 2, 2))).injectAEPower(10000, Actionable.MODULATE);
    }
    @GameTest(template="empty", batch="mae2_tax", timeoutTicks=300)
    public static void ordinaryEuTaxIncludesVoltageAndAcceptedPackets(GameTestHelper h) {
        finiteNetwork(h, false);
        h.setBlock(3, 1, 1, GTMachines.ELECTRIC_FURNACE[1].getBlock());
        h.startSequence().thenWaitUntil(() -> TransferRegression.ready(h)).thenExecute(() -> {
            var part = ((IPartHost) h.getBlockEntity(new BlockPos(1, 1, 2))).getPart(Direction.NORTH);
            var power = part.getGridNode().getGrid().getEnergyService();
            double before = power.extractAEPower(1e9, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            var input = h.getLevel().getCapability(GTCapability.CAPABILITY_ENERGY_CONTAINER, h.absolutePos(new BlockPos(1, 1, 2)), Direction.NORTH);
            long amps = input.acceptEnergyFromNetwork(Direction.NORTH, 32, 2);
            double after = power.extractAEPower(1e9, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            double expected = PowerUnit.FE.convertTo(PowerUnit.AE, amps * 32.0 * FeCompat.ratio(false) * AEConfig.instance().getP2PTunnelEnergyTax());
            h.assertTrue(amps == 1 && Math.abs(before - after - expected) < 1e-6, "EU tax omitted voltage or charged rejected amperes");
        }).thenSucceed();
    }
    @GameTest(template="empty", batch="mae2_tax", timeoutTicks=300)
    public static void nerfedTaxPartialPaymentBlocksAndRecovers(GameTestHelper h) {
        finiteNetwork(h, true);
        h.startSequence().thenWaitUntil(() -> TransferRegression.ready(h)).thenExecute(() -> {
            var saved = MAE2.CONFIG;
            try {
                var old = saved.parts();
                MAE2.CONFIG = new MAE2Config(saved.client(), new MAE2Config.Parts(true, true, .05, old.rates()), saved.faulty());
                var part = ((IPartHost) h.getBlockEntity(new BlockPos(1, 1, 2))).getPart(Direction.NORTH);
                var grid = part.getGridNode().getGrid();
                var power = grid.getEnergyService();
                var service = grid.getService(MultiP2PService.class);
                service.setTickable();
                power.extractAEPower(1e9, Actionable.MODULATE, PowerMultiplier.CONFIG);
                double tax = 8 * PowerUnit.FE.convertTo(PowerUnit.AE, 32 * MultiP2PService.getNerfTax(1, 8) * FeCompat.ratio(false));
                h.assertTrue(power.injectPower(tax / 2, Actionable.MODULATE) == 0, "Partial tax funding failed");
                service.transferredAmps[1] = 8;
                service.onServerEndTick();
                h.assertTrue(!service.taxSatisfied && Math.abs(service.transferredAmps[1] - 4) < 1e-6, "Partial tax payment used incorrect units");
                var input = h.getLevel().getCapability(GTCapability.CAPABILITY_ENERGY_CONTAINER, h.absolutePos(new BlockPos(1, 1, 2)), Direction.NORTH);
                h.assertTrue(input != null && input.acceptEnergyFromNetwork(Direction.NORTH, 32, 1) == 0, "Unpaid network continued accepting EU");
                power.injectPower(10000, Actionable.MODULATE);
                service.onServerEndTick();
                h.assertTrue(service.taxSatisfied && service.transferredAmps[1] == 0, "Tax debt did not clear after recharge");
                h.assertTrue(input.acceptEnergyFromNetwork(Direction.NORTH, 32, 1) == 1, "EU did not recover after tax payment");
            } finally { MAE2.CONFIG = saved; }
        }).thenSucceed();
    }
}
