package stone.mae2.bootstrap;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import stone.mae2.MAE2;

public final class MAE2Components {
    public static final DeferredRegister<DataComponentType<?>> TYPES = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MAE2.MODID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CustomData>> FAULTY_DATA = TYPES.register("faulty_data", () ->
        DataComponentType.<CustomData>builder().persistent(CustomData.CODEC)
            .networkSynchronized(ByteBufCodecs.COMPOUND_TAG.map(CustomData::of, CustomData::copyTag)).build());
    private MAE2Components() {}
}
