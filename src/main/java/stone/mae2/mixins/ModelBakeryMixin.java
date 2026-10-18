package stone.mae2.mixins;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import stone.mae2.hooks.BuiltInModelHooks;

/** 1.21.1 getModel hook; AE2's own built-in lookup deliberately accepts only its namespace. */
@Mixin(ModelBakery.class)
public class ModelBakeryMixin {
    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void mae2$getModel(ResourceLocation id, CallbackInfoReturnable<UnbakedModel> cir) {
        var model = BuiltInModelHooks.getBuiltInModel(id);
        if (model != null) cir.setReturnValue(model);
    }
}
