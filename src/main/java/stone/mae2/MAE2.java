package stone.mae2;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import stone.mae2.bootstrap.*;

@Mod(MAE2.MODID)
public class MAE2 {
    public static final String MODID = "mae2";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static MAE2Config CONFIG = new MAE2Config(new MAE2Config.Client(1),
        new MAE2Config.Parts(true, false, 0.05,
            new MAE2Config.TickRates(new MAE2Config.TickRates.TickRate(1, 1),
                new MAE2Config.TickRates.TickRate(1, 1), new MAE2Config.TickRates.TickRate(5, 120))),
        new MAE2Config.Faulty(32768));

    public MAE2(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, MAE2Config.CLIENT);
        container.registerConfig(ModConfig.Type.COMMON, MAE2Config.COMMON);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Proxy.client().init(bus);
        } else {
            Proxy.server().init(bus);
        }
    }

    public static ResourceLocation toKey(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
