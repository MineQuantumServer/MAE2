/*
 * Based on Applied Energistics 2 PatternProviderTarget (c) 2021 TeamAppliedEnergistics.
 * Licensed under the GNU Lesser General Public License, version 3 or later.
 */
package stone.mae2.parts.p2p;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.me.storage.CompositeStorage;
import appeng.parts.automation.StackWorldBehaviors;
import appeng.util.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import java.util.IdentityHashMap;
import java.util.Set;

/** Resolves current capabilities through public APIs; never retains an unloaded endpoint. */
public final class PatternTargetCache {
    private final ServerLevel level;
    private final BlockPos pos;
    private final Direction side;
    private final IActionSource source;

    public PatternTargetCache(ServerLevel level, BlockPos pos, Direction side, IActionSource source) {
        this.level = level;
        this.pos = pos.immutable();
        this.side = side;
        this.source = source;
    }

    public PatternProviderTarget find() {
        if (!level.hasChunkAt(pos) || !Platform.areBlockEntitiesTicking(level, pos)) return null;
        MEStorage found = level.getCapability(AECapabilities.ME_STORAGE, pos, side);
        if (found == null) {
            var stores = new IdentityHashMap<AEKeyType, MEStorage>();
            for (var entry : StackWorldBehaviors.createExternalStorageStrategies(level, pos, side).entrySet()) {
                var storage = entry.getValue().createWrapper(false, () -> {});
                if (storage != null) stores.put(entry.getKey(), storage);
            }
            if (stores.isEmpty()) return null;
            found = new CompositeStorage(stores);
        }
        final MEStorage storage = found;
        return new PatternProviderTarget() {
            public long insert(AEKey what, long amount, Actionable mode) {
                return storage.insert(what, amount, mode, source);
            }
            public boolean containsPatternInput(Set<AEKey> inputs) {
                var policy = PatternPushContext.policy();
                for (var stack : storage.getAvailableStacks()) {
                    if (stack.getLongValue() > 0 && BlockingPolicies.blocks(policy, stack.getKey(), inputs)) return true;
                }
                return false;
            }
        };
    }
}
