package com.kasper.vcdistance;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server's addon requirement ({@code require_addon}): a while after a player joins, players who
 * have Simple Voice Chat but no addon (or one older than {@code min_addon_version}) get a message
 * or are disconnected, depending on the setting.
 */
public final class AddonCheck {

    /** Time to wait after joining for the addon's hello and the voice chat connection. */
    public static final long DELAY_TICKS = 200;

    /** What to do with a player. */
    public enum Action {
        NONE, MESSAGE, KICK
    }

    private final Map<UUID, Long> joined = new ConcurrentHashMap<>();
    private final Map<UUID, String> versions = new ConcurrentHashMap<>();
    /** Players told once this server run (suggest mode). */
    private final Set<UUID> told = ConcurrentHashMap.newKeySet();

    public void joined(UUID player, long tick) {
        joined.put(player, tick);
        versions.remove(player);
    }

    /** The player's addon said hello, with its version ("1.8.0+mc26.x" or "1.8.0"). */
    public void hello(UUID player, String version) {
        versions.put(player, version == null ? "" : version);
    }

    public void left(UUID player) {
        joined.remove(player);
        versions.remove(player);
    }

    /** The addon version a player has, or {@code null} when they have none. */
    public String version(UUID player) {
        return versions.get(player);
    }

    /**
     * Called every tick or so; returns what to do with {@code player} now, once per join.
     *
     * @param hasVoiceChat whether the player has Simple Voice Chat (the requirement ignores players without it)
     */
    public Action due(ServerSettings s, UUID player, long tick, boolean hasVoiceChat) {
        Long since = joined.get(player);
        if (since == null || tick - since < DELAY_TICKS) {
            return Action.NONE;
        }
        joined.remove(player);
        ServerSettings.RequireAddon mode = s.getRequireAddon();
        if (mode == ServerSettings.RequireAddon.OFF || !hasVoiceChat || satisfied(versions.get(player), s.getMinAddonVersion())) {
            return Action.NONE;
        }
        return switch (mode) {
            case KICK -> Action.KICK;
            case WARN -> Action.MESSAGE;
            case SUGGEST -> told.add(player) ? Action.MESSAGE : Action.NONE;
            default -> Action.NONE;
        };
    }

    /** Whether an addon version (null = none) meets the minimum ("" = any). */
    static boolean satisfied(String version, String minimum) {
        if (version == null) {
            return false;
        }
        return minimum == null || minimum.isBlank() || compare(version, minimum) >= 0;
    }

    /** Compares "1.8.0+mc26.x" with "1.8": numbers part by part, whatever follows '+' or '-' ignored. */
    static int compare(String a, String b) {
        int[] x = parts(a);
        int[] y = parts(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0;
            int q = i < y.length ? y[i] : 0;
            if (p != q) {
                return Integer.compare(p, q);
            }
        }
        return 0;
    }

    private static int[] parts(String v) {
        String core = v.trim().split("[+\\- ]", 2)[0];
        String[] p = core.split("\\.");
        int[] out = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            try {
                out[i] = Integer.parseInt(p[i].replaceAll("\\D", ""));
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
