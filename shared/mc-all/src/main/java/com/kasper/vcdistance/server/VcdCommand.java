package com.kasper.vcdistance.server;

import com.kasper.vcdistance.AdminCommands;
import com.kasper.vcdistance.AudioDistancePlugin;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

/**
 * {@code /vcd} on a Fabric server. The text after the command goes to {@link AdminCommands} as is,
 * so every version registers the same small tree; only the permission check differs by version.
 */
public final class VcdCommand {

    /** What the version's server entrypoint provides. */
    public interface Server {
        void resendProfiles(MinecraftServer server);
    }

    private VcdCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Predicate<CommandSourceStack> admin, Server hooks) {
        dispatcher.register(Commands.literal(AdminCommands.NAME)
                .requires(admin)
                .executes(ctx -> run(ctx, "", hooks))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> suggest(builder))
                        .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "args"), hooks))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String args, Server hooks) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        AdminCommands.Context context = new AdminCommands.Context() {
            @Override
            public String platform() {
                return "Fabric";
            }

            @Override
            public int onlinePlayers() {
                return server.getPlayerCount();
            }

            @Override
            public int addonPlayers() {
                int n = 0;
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (AudioDistancePlugin.SERVER_WALLS.hasAddon(p.getUUID())) {
                        n++;
                    }
                }
                return n;
            }

            @Override
            public void resendProfiles() {
                hooks.resendProfiles(server);
            }
        };
        for (String line : AdminCommands.run(args, AudioDistancePlugin.SERVER_SETTINGS, context)) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(SuggestionsBuilder builder) {
        String typed = builder.getRemaining();
        int lastSpace = typed.lastIndexOf(' ');
        SuggestionsBuilder word = builder.createOffset(builder.getStart() + lastSpace + 1);
        for (String s : AdminCommands.suggest(typed)) {
            word.suggest(s);
        }
        return word.buildFuture();
    }
}
