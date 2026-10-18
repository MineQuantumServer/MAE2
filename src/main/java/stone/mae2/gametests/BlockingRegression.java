package stone.mae2.gametests;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import stone.mae2.parts.p2p.*;
import java.util.Set;

@GameTestHolder("mae2")
@PrefixGameTestTemplate(false)
public class BlockingRegression {
    @GameTest(template="empty")
    public static void allSmartDefaultAndCircuitExclusion(GameTestHelper h) {
        var pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, Blocks.BARREL);
        var inv = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(pos), null);
        h.assertTrue(inv != null, "Missing inventory");
        var target = new PatternTargetCache(h.getLevel(), h.absolutePos(pos), Direction.SOUTH, IActionSource.empty()).find();
        h.assertTrue(target != null, "No target wrapper");
        var inputs = Set.<appeng.api.stacks.AEKey>of(AEItemKey.of(Items.IRON_INGOT));
        inv.insertItem(0, new ItemStack(Items.OAK_PLANKS), false);
        assertPolicy(h, target, inputs, BlockingPolicy.DEFAULT, false);
        assertPolicy(h, target, inputs, BlockingPolicy.ALL, true);
        assertPolicy(h, target, inputs, BlockingPolicy.SMART, true);
        inv.extractItem(0, 64, false);
        inv.insertItem(0, new ItemStack(Items.IRON_INGOT), false);
        target = new PatternTargetCache(h.getLevel(), h.absolutePos(pos), Direction.SOUTH, IActionSource.empty()).find();
        assertPolicy(h, target, inputs, BlockingPolicy.DEFAULT, true);
        assertPolicy(h, target, inputs, BlockingPolicy.SMART, false);
        inv.extractItem(0, 64, false);
        var circuit = BuiltInRegistries.ITEM.get(ResourceLocation.parse("gtceu:programmed_circuit"));
        h.assertTrue(circuit != Items.AIR, "Specified GT circuit item missing");
        inv.insertItem(0, new ItemStack(circuit), false);
        target = new PatternTargetCache(h.getLevel(), h.absolutePos(pos), Direction.SOUTH, IActionSource.empty()).find();
        for (var policy : BlockingPolicy.values()) assertPolicy(h, target, inputs, policy, false);
        h.succeed();
    }
    private static void assertPolicy(GameTestHelper h, appeng.helpers.patternprovider.PatternProviderTarget target,
            Set<appeng.api.stacks.AEKey> inputs, BlockingPolicy policy, boolean expected) {
        PatternPushContext.push(true, policy);
        try { h.assertTrue(target.containsPatternInput(inputs) == expected, "Incorrect " + policy + " blocking"); }
        finally { PatternPushContext.pop(); }
    }
}
