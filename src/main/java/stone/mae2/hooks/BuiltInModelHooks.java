package stone.mae2.hooks;

import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import stone.mae2.MAE2;
import java.util.HashMap;
import java.util.Map;

public final class BuiltInModelHooks {
    private static final Map<ResourceLocation, UnbakedModel> MODELS = new HashMap<>();
    private BuiltInModelHooks() {}
    public static void addBuiltInModel(ResourceLocation id, UnbakedModel model) {
        if (!id.getNamespace().equals(MAE2.MODID) || MODELS.putIfAbsent(id, model) != null)
            throw new IllegalArgumentException("Invalid or duplicate MAE2 model: " + id);
    }
    public static UnbakedModel getBuiltInModel(ResourceLocation id) { return MODELS.get(id); }
}
