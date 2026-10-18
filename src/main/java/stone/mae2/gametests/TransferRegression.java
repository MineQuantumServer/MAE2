package stone.mae2.gametests;

import appeng.api.ids.AEComponents;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.util.SettingsFrom;
import com.gregtechceu.gtceu.api.capability.GTCapability;
import com.gregtechceu.gtceu.common.data.GTMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import stone.mae2.MAE2;
import stone.mae2.bootstrap.MAE2Items;
import stone.mae2.integration.GregTechIntegration;
import stone.mae2.parts.p2p.multi.MultiP2PTunnel;

@GameTestHolder(MAE2.MODID)
@PrefixGameTestTemplate(false)
public class TransferRegression {
    private static BlockPos cable(int x) { return new BlockPos(x, 1, 2); }
    private static BlockPos device(int x) { return new BlockPos(x, 1, 1); }

    static void network(GameTestHelper h, IPartItem<?> item) {
        for (int x = 1; x <= 3; x++) {
            h.setBlock(cable(x), AEBlocks.CABLE_BUS.block());
            var host = (IPartHost) h.getBlockEntity(cable(x));
            host.addPart(AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT), null, null);
            if (x == 2 && appeng.parts.p2p.P2PTunnelPart.class.isAssignableFrom(item.getPartClass())) continue;
            var part = host.addPart(item, Direction.NORTH, null);
            if (x == 3) {
                part.importSettings(SettingsFrom.MEMORY_CARD,
                    DataComponentMap.builder().set(AEComponents.EXPORTED_P2P_FREQUENCY, (short) 7211).build(), null);
            } else if (part instanceof MultiP2PTunnel.Part<?, ?, ?> tunnel) tunnel.setFrequency((short) 7211);
            else ((appeng.parts.p2p.P2PTunnelPart<?>) part).setFrequency((short) 7211);
        }
        h.setBlock(2, 2, 2, AEBlocks.CREATIVE_ENERGY_CELL.block());
    }
    static void ready(GameTestHelper h) {
        for (int x = 1; x <= 3; x++) {
            var part = ((IPartHost) h.getBlockEntity(cable(x))).getPart(Direction.NORTH);
            if (part == null) continue;
            h.assertTrue(part.getGridNode() != null && part.getGridNode().isActive(), "Tunnel not active");
        }
    }
    private static <T> T cap(GameTestHelper h, net.neoforged.neoforge.capabilities.BlockCapability<T, Direction> cap, BlockPos pos) {
        var value = h.getLevel().getCapability(cap, h.absolutePos(pos), Direction.NORTH);
        h.assertTrue(value != null, "Capability not exposed: " + cap.name());
        return value;
    }

    @GameTest(template="empty", timeoutTicks=200)
    public static void feSimulationSaveAndRebind(GameTestHelper h) {
        network(h, MAE2Items.FE_MULTI_P2P_TUNNEL.get());
        h.startSequence().thenWaitUntil(() -> ready(h)).thenExecute(() -> {
            var input = cap(h, Capabilities.EnergyStorage.BLOCK, cable(1));
            var output = cap(h, Capabilities.EnergyStorage.BLOCK, cable(3));
            h.assertTrue(input.receiveEnergy(500, true) == 500 && output.getEnergyStored() == 0, "Simulation changed FE");
            h.assertTrue(input.receiveEnergy(500, false) == 500, "FE input rejected");
            h.assertTrue(output.extractEnergy(125, true) == 125 && output.getEnergyStored() == 500, "Simulated extraction changed FE");
            h.assertTrue(output.extractEnergy(125, false) == 125 && output.getEnergyStored() == 375, "FE is not conserved");
            var part = (MultiP2PTunnel.Part<?, ?, ?>) ((IPartHost) h.getBlockEntity(cable(1))).getPart(Direction.NORTH);
            part.writeToNBT(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess());
            part.writeToNBT(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess());
            h.assertTrue(output.getEnergyStored() == 375, "Saving depleted energy buffer");
            part.setFrequency((short) 7212);
            h.assertTrue(input.receiveEnergy(10, false) == 0, "Old capability remained live after rebind");
        }).thenSucceed();
    }

    @GameTest(template="empty", timeoutTicks=200)
    public static void feSharesAndPendingTaxSurviveRebind(GameTestHelper h) {
        network(h, MAE2Items.FE_MULTI_P2P_TUNNEL.get());
        h.startSequence().thenWaitUntil(() -> ready(h)).thenExecute(() -> {
            var input = cap(h, Capabilities.EnergyStorage.BLOCK, cable(1));
            var output = cap(h, Capabilities.EnergyStorage.BLOCK, cable(3));
            input.receiveEnergy(500, false);
            output.extractEnergy(125, false);
            var part = (MultiP2PTunnel.Part<?, ?, ?>) ((IPartHost) h.getBlockEntity(cable(1))).getPart(Direction.NORTH);
            part.setFrequency((short) 7212);
            h.assertTrue(output.getEnergyStored() == 250, "Frequency split lost or duplicated the moving endpoint's share");
            part.setFrequency((short) 7211);
            h.assertTrue(output.getEnergyStored() == 375, "Frequency merge lost buffer share");
            long energy = 0, tax = 0;
            for (int x = 1; x <= 3; x++) {
                var saved = h.getBlockEntity(cable(x)).saveWithFullMetadata(h.getLevel().registryAccess());
                var shared = saved.getCompound("north").getCompound("gn").getCompound("mae2:multi_p2p");
                energy += shared.getLong("energy"); tax += shared.getLong("pendingTax");
            }
            h.assertTrue(energy == 375 && tax == 125, "Persisted energy/tax shares do not sum to the original amounts");
            var service = part.getGridNode().getGrid().getService(stone.mae2.me.service.MultiP2PService.class);
            var chunk = h.getLevel().getChunkAt(h.absolutePos(cable(1)));
            chunk.setUnsaved(false);
            service.onServerEndTick();
            h.assertTrue(chunk.isUnsaved(), "Changing shared energy did not mark endpoint chunks for saving");
        }).thenSucceed();
    }

    @GameTest(template="empty", timeoutTicks=200)
    public static void itemsFullPartialAndSimulated(GameTestHelper h) {
        network(h, MAE2Items.ITEM_MULTI_P2P_TUNNEL.get());
        h.setBlock(device(3), Blocks.BARREL);
        h.startSequence().thenWaitUntil(() -> ready(h)).thenExecute(() -> {
            var sink = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(device(3)), null);
            h.assertTrue(sink != null, "Missing barrel inventory");
            sink.insertItem(0, new ItemStack(Items.IRON_INGOT, 60), false);
            for (int i = 1; i < sink.getSlots(); i++) sink.insertItem(i, new ItemStack(Items.DIRT, 64), false);
            var input = cap(h, Capabilities.ItemHandler.BLOCK, cable(1));
            var offered = new ItemStack(Items.IRON_INGOT, 8);
            h.assertTrue(input.insertItem(0, offered, true).getCount() == 4 && sink.getStackInSlot(0).getCount() == 60, "Item simulation mutated or misreported");
            h.assertTrue(input.insertItem(0, offered, false).getCount() == 4 && sink.getStackInSlot(0).getCount() == 64, "Partial item insertion lost material");
            h.assertTrue(input.insertItem(0, offered, false).getCount() == 8, "Full destination consumed material");
        }).thenSucceed();
    }

    @GameTest(template="empty", timeoutTicks=200)
    public static void fluidMultiInputDrainsConserve(GameTestHelper h) {
        network(h, MAE2Items.FLUID_MULTI_P2P_TUNNEL.get());
        h.setBlock(device(1), GTMachines.WOODEN_DRUM.getBlock());
        h.setBlock(device(2), GTMachines.WOODEN_DRUM.getBlock());
        h.startSequence().thenWaitUntil(() -> ready(h)).thenExecute(() -> {
            var a = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(device(1)), Direction.SOUTH);
            var b = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(device(2)), Direction.SOUTH);
            h.assertTrue(a != null && b != null, "Missing GT drum capability");
            h.assertTrue(a.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 1000 && b.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 1000, "Drum initialization failed");
            var output = cap(h, Capabilities.FluidHandler.BLOCK, cable(3));
            h.assertTrue(output.drain(new FluidStack(Fluids.WATER, 1500), FluidAction.SIMULATE).getAmount() == 1500, "Fluid simulation amount incorrect");
            h.assertTrue(a.getFluidInTank(0).getAmount() + b.getFluidInTank(0).getAmount() == 2000, "Simulation consumed fluid");
            h.assertTrue(output.drain(new FluidStack(Fluids.WATER, 1500), FluidAction.EXECUTE).getAmount() == 1500, "Multi input fluid amount incorrect");
            h.assertTrue(a.getFluidInTank(0).getAmount() + b.getFluidInTank(0).getAmount() == 500, "Multi input drain lost fluid");
            h.assertTrue(output.drain(1000, FluidAction.EXECUTE).getAmount() == 500, "Untyped drain lost its first withdrawal");
            h.assertTrue(a.getFluidInTank(0).isEmpty() && b.getFluidInTank(0).isEmpty(), "Unexpected remaining fluid");
        }).thenSucceed();
    }

    @GameTest(template="empty", timeoutTicks=200)
    public static void euIntoRealGtMachine(GameTestHelper h) {
        runEu(h, true);
    }
    @GameTest(template="empty", timeoutTicks=200)
    public static void ordinaryEuIntoRealGtMachine(GameTestHelper h) { runEu(h, false); }
    private static void runEu(GameTestHelper h, boolean multi) {
        network(h, multi ? GregTechIntegration.EU_MULTI_P2P_TUNNEL.get() : GregTechIntegration.EU_P2P_TUNNEL.get());
        h.setBlock(device(3), GTMachines.ELECTRIC_FURNACE[1].getBlock());
        var accepted = new java.util.concurrent.atomic.AtomicLong();
        h.startSequence().thenWaitUntil(() -> ready(h)).thenExecute(() -> {
            var sink = h.getLevel().getCapability(GTCapability.CAPABILITY_ENERGY_CONTAINER, h.absolutePos(device(3)), Direction.SOUTH);
            h.assertTrue(sink != null, "Missing GT furnace energy capability");
            var input = cap(h, GTCapability.CAPABILITY_ENERGY_CONTAINER, cable(1));
            h.assertTrue(!input.inputsEnergy(Direction.SOUTH), "EU advertised the wrong direction");
            h.assertTrue(input.acceptEnergyFromNetwork(Direction.SOUTH, 32, 2) == 0, "EU accepted wrong face");
            long received = input.acceptEnergyFromNetwork(Direction.NORTH, 32, 2);
            var p = ((IPartHost) h.getBlockEntity(cable(1))).getPart(Direction.NORTH);
            var o = ((IPartHost) h.getBlockEntity(cable(3))).getPart(Direction.NORTH);
            accepted.set(received);
            h.assertTrue(received > 0 && received <= 2, "EU packets rejected: received=" + received + ", input=" + p.getGridNode().isActive()
                + ", output=" + o.getGridNode().isActive() + ", outputs=" + (p instanceof appeng.parts.p2p.P2PTunnelPart<?> t ? t.getOutputs().size() : -1)
                + ", sinkInput=" + sink.inputsEnergy(Direction.SOUTH) + ", sinkV=" + sink.getInputVoltage());
        }).thenIdle(2).thenExecute(() -> {
            var sink = h.getLevel().getCapability(GTCapability.CAPABILITY_ENERGY_CONTAINER, h.absolutePos(device(3)), Direction.SOUTH);
            h.assertTrue(sink.getEnergyStored() == accepted.get() * 32, "GT accepted amperes were confused with EU amount");
            if (accepted.get() < 2) {
                var input = cap(h, GTCapability.CAPABILITY_ENERGY_CONTAINER, cable(1));
                long remaining = 2 - accepted.get();
                h.assertTrue(input.acceptEnergyFromNetwork(Direction.NORTH, 32, remaining) == remaining, "GT per tick limit did not reset");
                h.assertTrue(sink.getEnergyStored() == 64, "Partial EU delivery not conserved");
            }
        }).thenSucceed();
    }
    @GameTest(template="empty", timeoutTicks=200)
    public static void euPacketOverflowIsBounded(GameTestHelper h) {
        network(h, GregTechIntegration.EU_MULTI_P2P_TUNNEL.get());
        h.startSequence().thenWaitUntil(() -> ready(h)).thenExecute(() -> {
            var input = cap(h, GTCapability.CAPABILITY_ENERGY_CONTAINER, cable(1));
            var output = cap(h, GTCapability.CAPABILITY_ENERGY_CONTAINER, cable(3));
            h.assertTrue(input.acceptEnergyFromNetwork(Direction.NORTH, Long.MAX_VALUE, Long.MAX_VALUE) == 1, "Overflowing EU packet count");
            h.assertTrue(output.getEnergyStored() == Long.MAX_VALUE, "EU buffer overflowed");
            h.assertTrue(input.acceptEnergyFromNetwork(Direction.NORTH, 32, 1) == 0, "Full EU buffer accepted more");
            h.assertTrue(input.acceptEnergyFromNetwork(Direction.NORTH, 0, 1) == 0, "Zero voltage accepted");
        }).thenSucceed();
    }

}
