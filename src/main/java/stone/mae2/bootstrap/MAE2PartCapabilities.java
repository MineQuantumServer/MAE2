package stone.mae2.bootstrap;

import appeng.api.AECapabilities;
import appeng.api.parts.RegisterPartCapabilitiesEvent;
import com.gregtechceu.gtceu.api.capability.GTCapability;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import stone.mae2.parts.p2p.*;
import stone.mae2.parts.p2p.multi.*;

public final class MAE2PartCapabilities {
    private MAE2PartCapabilities() {}
    public static void init(IEventBus bus) { bus.addListener(MAE2PartCapabilities::register); }
    private static void register(RegisterPartCapabilitiesEvent event) {
        event.register(Capabilities.ItemHandler.BLOCK, (ItemMultiP2PTunnel.Part p, net.minecraft.core.Direction s) -> p.getCapability(Capabilities.ItemHandler.BLOCK), ItemMultiP2PTunnel.Part.class);
        event.register(Capabilities.FluidHandler.BLOCK, (FluidMultiP2PTunnel.Part p, net.minecraft.core.Direction s) -> p.getCapability(Capabilities.FluidHandler.BLOCK), FluidMultiP2PTunnel.Part.class);
        event.register(Capabilities.EnergyStorage.BLOCK, (FEMultiP2PTunnel.Part p, net.minecraft.core.Direction s) -> p.getCapability(Capabilities.EnergyStorage.BLOCK), FEMultiP2PTunnel.Part.class);
        event.register(GTCapability.CAPABILITY_ENERGY_CONTAINER, (EUMultiP2PTunnel.Part p, net.minecraft.core.Direction s) -> MAE2Enabled.eu(p) ? p.getCapability(GTCapability.CAPABILITY_ENERGY_CONTAINER) : null, EUMultiP2PTunnel.Part.class);
        event.register(GTCapability.CAPABILITY_ENERGY_CONTAINER, (EUP2PTunnelPart p, net.minecraft.core.Direction s) -> MAE2Enabled.eu(p) ? p.getExposedApi() : null, EUP2PTunnelPart.class);
        pattern(event, AECapabilities.CRAFTING_MACHINE);
        pattern(event, AECapabilities.ME_STORAGE);
        pattern(event, AECapabilities.GENERIC_INTERNAL_INV);
        pattern(event, Capabilities.ItemHandler.BLOCK);
        pattern(event, Capabilities.FluidHandler.BLOCK);
    }
    private static <T> void pattern(RegisterPartCapabilitiesEvent event,
            net.neoforged.neoforge.capabilities.BlockCapability<T, net.minecraft.core.Direction> cap) {
        event.register(cap, (PatternP2PTunnelPart p, net.minecraft.core.Direction s) -> p.getCapability(cap), PatternP2PTunnelPart.class);
        event.register(cap, (PatternMultiP2PTunnel.Part p, net.minecraft.core.Direction s) -> p.getCapability(cap), PatternMultiP2PTunnel.Part.class);
    }
    private static class MAE2Enabled {
        static boolean eu(appeng.parts.AEBasePart p) {
            return stone.mae2.MAE2.CONFIG.parts().isEUP2PEnabled() && p.getMainNode().isActive();
        }
    }
}
