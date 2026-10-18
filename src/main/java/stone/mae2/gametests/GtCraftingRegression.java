package stone.mae2.gametests;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.networking.crafting.*;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.helpers.MachineSource;
import appeng.parts.p2p.P2PTunnelPart;
import appeng.util.SettingsFrom;
import com.gregtechceu.gtceu.api.capability.GTCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import stone.mae2.bootstrap.MAE2Blocks;
import stone.mae2.bootstrap.MAE2Items;
import stone.mae2.parts.p2p.multi.MultiP2PTunnel;
import java.util.List;
import java.util.concurrent.Future;

@GameTestHolder("mae2")
@PrefixGameTestTemplate(false)
public class GtCraftingRegression {
    private static final BlockPos PROVIDER = new BlockPos(1, 1, 1);
    private static final BlockPos GT = new BlockPos(3, 1, 1);
    private static final BlockPos BARREL = new BlockPos(0, 1, 2);

    @GameTest(template="empty", timeoutTicks=800)
    public static void ordinaryFullBlockToGt(GameTestHelper h) { run(h, false, false); }
    @GameTest(template="empty", timeoutTicks=800)
    public static void ordinaryPartToGt(GameTestHelper h) { run(h, false, true); }
    @GameTest(template="empty", timeoutTicks=800)
    public static void multiFullBlockToGt(GameTestHelper h) { run(h, true, false); }
    @GameTest(template="empty", timeoutTicks=800)
    public static void multiPartToGt(GameTestHelper h) { run(h, true, true); }

    private static void cable(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, AEBlocks.CABLE_BUS.block());
        ((IPartHost) h.getBlockEntity(pos)).addPart(AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT), null, null);
    }
    private static void run(GameTestHelper h, boolean multi, boolean multipart) {
        for (int x = 1; x <= 3; x++) cable(h, new BlockPos(x, 1, 2));
        cable(h, new BlockPos(1, 0, 1));
        cable(h, new BlockPos(1, 0, 2));
        for (int x : new int[]{1, 3}) {
            var host = (IPartHost) h.getBlockEntity(new BlockPos(x, 1, 2));
            appeng.api.parts.IPart part = multi ? host.addPart(MAE2Items.PATTERN_MULTI_P2P_TUNNEL.get(), Direction.NORTH, null)
                : host.addPart(MAE2Items.PATTERN_P2P_TUNNEL.get(), Direction.NORTH, null);
            if (x == 1) {
                if (part instanceof P2PTunnelPart<?> tunnel) tunnel.setFrequency((short) 7211);
                else ((MultiP2PTunnel.Part<?, ?, ?>) part).setFrequency((short) 7211);
            } else part.importSettings(SettingsFrom.MEMORY_CARD,
                DataComponentMap.builder().set(AEComponents.EXPORTED_P2P_FREQUENCY, (short) 7211).build(), null);
        }
        ((IPartHost) h.getBlockEntity(new BlockPos(1, 1, 2))).addPart(AEParts.STORAGE_BUS.get(), Direction.WEST, null);
        PatternProviderLogicHost provider;
        if (multipart) {
            cable(h, PROVIDER);
            provider = ((IPartHost) h.getBlockEntity(PROVIDER)).addPart(AEParts.PATTERN_PROVIDER.get(), Direction.SOUTH, null);
        } else {
            h.setBlock(PROVIDER, AEBlocks.PATTERN_PROVIDER.block());
            provider = (PatternProviderLogicHost) h.getBlockEntity(PROVIDER);
        }
        h.setBlock(2, 1, 3, AEBlocks.CRAFTING_STORAGE_1K.block());
        h.setBlock(2, 1, 4, MAE2Blocks.ACCELERATOR_256x.get());
        h.setBlock(2, 2, 2, AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(BARREL, Blocks.BARREL);
        h.setBlock(GT, GTMachines.ELECTRIC_FURNACE[1].getBlock());
        var machine = (MetaMachine) h.getBlockEntity(GT);
        var auto = machine.getTraitOrThrow(AutoOutputTrait.class);
        auto.setItemOutputDirection(Direction.SOUTH);
        auto.setAllowItemInputFromOutputSide(true);
        auto.setAllowAutoOutputItems(true);
        var pattern = PatternDetailsHelper.encodeProcessingPattern(
            List.of(new GenericStack(AEItemKey.of(Items.RAW_IRON), 1)),
            List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)));
        provider.getLogic().getPatternInv().setItemDirect(0, pattern);
        provider.getLogic().updatePatterns();
        var source = new MachineSource((appeng.api.networking.security.IActionHost) provider);
        var inventory = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(BARREL), null);
        inventory.insertItem(0, new ItemStack(Items.RAW_IRON, 1), false);
        h.onEachTick(() -> {
            var energy = h.getLevel().getCapability(GTCapability.CAPABILITY_ENERGY_CONTAINER, h.absolutePos(GT), Direction.EAST);
            if (energy != null) energy.acceptEnergyFromNetwork(Direction.EAST, 32, 2);
        });
        var future = new java.util.concurrent.atomic.AtomicReference<Future<ICraftingPlan>>();
        h.startSequence().thenWaitUntil(() -> {
            var grid = provider.getGrid();
            h.assertTrue(grid != null && !grid.getCraftingService().getCpus().isEmpty(), "CPU not available");
            h.assertTrue(grid.getStorageService().getInventory().getAvailableStacks().get(AEItemKey.of(Items.RAW_IRON)) == 1, "Raw iron not mounted");
            h.assertTrue(grid.getCraftingService().isCraftable(AEItemKey.of(Items.IRON_INGOT)), "Processing pattern not available");
        }).thenExecute(() -> future.set(provider.getGrid().getCraftingService().beginCraftingCalculation(h.getLevel(),
            () -> source, AEItemKey.of(Items.IRON_INGOT), 1, CalculationStrategy.REPORT_MISSING_ITEMS)))
          .thenWaitUntil(() -> h.assertTrue(future.get().isDone(), "Craft calculation pending"))
          .thenExecute(() -> {
              try {
                  var result = provider.getGrid().getCraftingService().submitJob(future.get().get(), null, null, false, source);
                  h.assertTrue(result.successful(), "GT crafting submission failed: " + result.errorCode());
              } catch (Exception e) { throw new RuntimeException(e); }
          }).thenWaitUntil(() -> {
              long returned = provider.getGrid().getStorageService().getInventory().getAvailableStacks().get(AEItemKey.of(Items.IRON_INGOT));
              h.assertTrue(returned == 1, "GT product not returned through pattern tunnel");
              h.assertTrue(provider.getGrid().getCraftingService().getCpus().stream().noneMatch(ICraftingCPU::isBusy), "Crafting job did not complete");
          }).thenSucceed();
    }
}
