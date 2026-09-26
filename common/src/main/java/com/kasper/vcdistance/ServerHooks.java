package com.kasper.vcdistance;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The server-side events every platform reports the same way (Fabric, NeoForge, Paper): the players
 * online, joins and leaves, the addon's hello, and commands from the Server tab. The platform only
 * turns its players into {@link ServerPlayers.Info} and delivers messages.
 */
public final class ServerHooks {

    /** How often the players' positions and states are refreshed for the voice rules. */
    public static final int REFRESH_TICKS = 5;

    /** What a platform does for these hooks. */
    public interface Platform {

        void message(UUID player, String text);

        void kick(UUID player, String text);
    }

    private static long ticks;

    private ServerHooks() {
    }

    /** Whether this tick should refresh the players (call {@link #refresh} then). */
    public static boolean refreshDue() {
        return ++ticks % REFRESH_TICKS == 0;
    }

    /** New snapshot of the players online; also carries out the addon requirement. */
    public static void refresh(Collection<ServerPlayers.Info> online, Platform platform) {
        ServerPlayers players = AudioDistancePlugin.PLAYERS;
        Set<UUID> ids = new HashSet<>();
        for (ServerPlayers.Info info : online) {
            players.update(info);
            ids.add(info.id());
        }
        for (ServerPlayers.Info known : List.copyOf(players.all())) {
            if (!ids.contains(known.id())) {
                players.remove(known.id());
            }
        }
        ServerSettings settings = AudioDistancePlugin.SERVER_SETTINGS;
        for (ServerPlayers.Info info : online) {
            AddonCheck.Action action = AudioDistancePlugin.ADDON_CHECK.due(settings, info.id(), ticks,
                    AudioDistancePlugin.hasVoiceChat(info.id()));
            if (action == AddonCheck.Action.NONE) {
                continue;
            }
            String text = requirementText(settings, info);
            if (action == AddonCheck.Action.KICK) {
                platform.kick(info.id(), text);
            } else {
                platform.message(info.id(), text);
            }
        }
    }

    /** "Install the addon" or "update the addon", in the player's language. */
    static String requirementText(ServerSettings settings, ServerPlayers.Info info) {
        String language = settings.languageFor(info.language());
        String version = AudioDistancePlugin.ADDON_CHECK.version(info.id());
        if (version != null && !settings.getMinAddonVersion().isEmpty()) {
            return ServerText.get(language, "require.update", settings.getMinAddonVersion(), settings.getAddonUrl());
        }
        return ServerText.get(language, "require.message", settings.getAddonUrl());
    }

    public static void joined(UUID player) {
        AudioDistancePlugin.ADDON_CHECK.joined(player, ticks);
    }

    public static void left(UUID player) {
        AudioDistancePlugin.ADDON_CHECK.left(player);
        AudioDistancePlugin.PLAYERS.remove(player);
        AudioDistancePlugin.SERVER_WALLS.forgetPlayer(player);
        AudioDistancePlugin.ZONES.forget(player);
    }

    /** The addon's hello: remembers the player has it (and which version). */
    public static boolean hello(UUID player, String text) {
        if (LinkProtocol.parseHello(text) < 1) {
            return false;
        }
        AudioDistancePlugin.SERVER_WALLS.markAddonListener(player);
        AudioDistancePlugin.ADDON_CHECK.hello(player, LinkProtocol.helloVersion(text));
        return true;
    }

    /**
     * A command from a player's Server tab.
     *
     * @param admin whether the player may use {@code /vcd} (checked by the platform, never trusted from the client)
     * @return the reply to send back, or {@code null} to send nothing
     */
    public static String admin(UUID player, String text, boolean admin, AdminCommands.Context ctx) {
        String command = LinkProtocol.parseAdminRequest(text);
        if (command == null || !admin) {
            return null;
        }
        List<String> lines = AdminCommands.run(command, AudioDistancePlugin.SERVER_SETTINGS, ctx);
        return LinkProtocol.adminReply(lines, AudioDistancePlugin.SERVER_SETTINGS);
    }
}
