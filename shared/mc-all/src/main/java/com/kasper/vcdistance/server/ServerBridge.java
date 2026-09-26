package com.kasper.vcdistance.server;

import com.kasper.vcdistance.AdminCommands;
import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.ServerHooks;
import com.kasper.vcdistance.ServerPlayers;
import com.kasper.vcdistance.Zone;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the server side needs from Minecraft on Fabric and NeoForge (both use Minecraft's own
 * classes): players as the voice rules see them, admin rights, zones and the command context.
 */
public final class ServerBridge {

    private ServerBridge() {
    }

    /** A player as the voice rules see them. */
    public static ServerPlayers.Info info(ServerPlayer p) {
        return new ServerPlayers.Info(p.getUUID(), p.getName().getString(),
                ServerZones.dimensionId(String.valueOf(p.level().dimension())),
                p.getX(), p.getY(), p.getZ(),
                p.isShiftKeyDown(), p.isAlive(), p.isSpectator(),
                item(p.getMainHandItem()), item(p.getOffhandItem()), List.of(), PlayerLanguage.of(p));
    }

    private static String item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** Server tick: refreshes the players for the voice rules and carries out the addon requirement. */
    public static void tick(MinecraftServer server) {
        if (!ServerHooks.refreshDue()) {
            return;
        }
        List<ServerPlayers.Info> online = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            try {
                online.add(info(p));
            } catch (Throwable ignored) {
                // A player half-way through joining or leaving
            }
        }
        ServerHooks.refresh(online, new ServerHooks.Platform() {
            @Override
            public void message(UUID player, String text) {
                ServerPlayer p = server.getPlayerList().getPlayer(player);
                if (p != null) {
                    p.sendSystemMessage(Component.literal(text));
                }
            }

            @Override
            public void kick(UUID player, String text) {
                ServerPlayer p = server.getPlayerList().getPlayer(player);
                if (p != null) {
                    p.connection.disconnect(Component.literal(text));
                }
            }
        });
    }

    /** Whether the player may use /vcd (and so gets the Server tab). */
    public static boolean isAdmin(ServerPlayer player) {
        try {
            return AdminPermission.isAdmin(player.createCommandSourceStack());
        } catch (Throwable t) {
            return false;
        }
    }

    /** The zone a player is in right now, or {@code null}. */
    public static Zone zoneOf(ServerPlayer player) {
        return AudioDistancePlugin.SERVER_SETTINGS.zoneOf(info(player));
    }

    /** "NeoForge" or "Fabric", for /vcd status. */
    public static String platform() {
        try {
            Class.forName("net.neoforged.fml.common.Mod");
            return "NeoForge";
        } catch (ClassNotFoundException e) {
            return "Fabric";
        }
    }

    /** What /vcd needs from the server, for a command run by {@code sender} ({@code null}: the console). */
    public static AdminCommands.Context context(MinecraftServer server, UUID sender, VcdCommand.Server hooks) {
        return new AdminCommands.Context() {
            @Override
            public String platform() {
                return ServerBridge.platform();
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

            @Override
            public UUID sender() {
                return sender;
            }
        };
    }

    /**
     * A command from a player's Server tab: runs it when the player is an admin.
     *
     * @return the reply to send back, or {@code null}
     */
    public static String admin(ServerPlayer player, String text, VcdCommand.Server hooks) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return null;
        }
        // The command needs the admin's position (zone pos1...): refresh them first
        AudioDistancePlugin.PLAYERS.update(info(player));
        return ServerHooks.admin(player.getUUID(), text, isAdmin(player), context(server, player.getUUID(), hooks));
    }
}
