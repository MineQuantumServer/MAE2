package stone.mae2.parts.p2p;

import appeng.api.config.Setting;
import appeng.api.stacks.AEKey;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

public final class BlockingPolicies {
    public static final Setting<BlockingPolicy> SETTING = new Setting<>("blocking_mode_extra", BlockingPolicy.class);
    private static final ResourceLocation CIRCUIT = ResourceLocation.fromNamespaceAndPath("gtceu", "programmed_circuit");
    private BlockingPolicies() {}
    public static boolean blocks(BlockingPolicy policy, AEKey key, Set<AEKey> inputs) {
        if (key.getId().equals(CIRCUIT)) return false;
        return switch (policy) {
            case ALL -> true;
            case DEFAULT -> inputs.contains(key.dropSecondary());
            case SMART -> !inputs.contains(key.dropSecondary());
        };
    }
}
