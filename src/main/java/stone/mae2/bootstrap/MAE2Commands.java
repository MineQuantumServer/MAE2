package stone.mae2.bootstrap;

import appeng.api.parts.IPartHost;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import stone.mae2.item.faulty.UberMode;
import stone.mae2.parts.p2p.BlockingPolicy;
import java.util.Locale;

public final class MAE2Commands {
    private MAE2Commands() {}
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mae2").then(Commands.literal("blocking")
            .then(Commands.argument("mode", StringArgumentType.word())
                .suggests((context, builder) -> {
                    for (var value : BlockingPolicy.values()) builder.suggest(value.name().toLowerCase(Locale.ROOT));
                    return builder.buildFuture();
                }).executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    var hit = UberMode.rayTrace(player.level(), player);
                    if (!player.level().mayInteract(player, hit.getBlockPos())) return 0;
                    Object target = player.level().getBlockEntity(hit.getBlockPos());
                    if (target instanceof IPartHost host) target = host.selectPartWorld(hit.getLocation()).part;
                    if (!(target instanceof PatternProviderLogicHost provider)) {
                        context.getSource().sendFailure(Component.translatable("gui.mae2.blocking.no_provider"));
                        return 0;
                    }
                    BlockingPolicy policy;
                    try { policy = BlockingPolicy.valueOf(StringArgumentType.getString(context, "mode").toUpperCase(Locale.ROOT)); }
                    catch (IllegalArgumentException e) { return 0; }
                    provider.getLogic().getConfigManager().putSetting(stone.mae2.parts.p2p.BlockingPolicies.SETTING, policy);
                    context.getSource().sendSuccess(() -> Component.translatable("gui.mae2.blocking.changed", policy.name()), false);
                    return 1;
                }))));
    }
}
