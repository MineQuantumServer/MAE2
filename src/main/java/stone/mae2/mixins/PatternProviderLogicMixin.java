package stone.mae2.mixins;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import stone.mae2.parts.p2p.PatternP2PTunnelLogic;
import stone.mae2.parts.p2p.PatternPushContext;
import java.util.Set;

@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderLogicMixin {
    @Shadow @Final private PatternProviderLogicHost host;
    @Shadow public abstract boolean isBlocking();
    @Shadow @Final private appeng.api.util.IConfigManager configManager;

    @Inject(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V", at = @At("RETURN"))
    private void mae2$registerBlockingPolicy(CallbackInfo ci) {
        ((appeng.util.ConfigManager) configManager).registerSetting(
            stone.mae2.parts.p2p.BlockingPolicies.SETTING, stone.mae2.parts.p2p.BlockingPolicy.DEFAULT);
    }
    @Shadow private Set<Direction> getActiveSides() { throw new AssertionError(); }

    @WrapMethod(method = "pushPattern")
    private boolean mae2$withBlocking(IPatternDetails pattern, KeyCounter[] ingredients, Operation<Boolean> original) {
        PatternPushContext.push(isBlocking(), configManager.getSetting(stone.mae2.parts.p2p.BlockingPolicies.SETTING));
        try {
            return original.call(pattern, ingredients);
        } finally {
            PatternPushContext.pop();
        }
    }

    @Inject(method = "updatePatterns", at = @At("RETURN"))
    private void mae2$refreshPatterns(CallbackInfo ci) {
        var be = host.getBlockEntity();
        var level = be.getLevel();
        if (level == null || level.isClientSide()) return;
        for (var direction : getActiveSides()) {
            var pos = be.getBlockPos().relative(direction);
            if (level.hasChunkAt(pos) && ICraftingMachine.of(level, pos, direction.getOpposite()) instanceof PatternP2PTunnelLogic logic) {
                logic.refreshInputs();
            }
        }
    }
}
