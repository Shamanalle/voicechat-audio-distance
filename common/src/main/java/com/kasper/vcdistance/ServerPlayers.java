package com.kasper.vcdistance;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the server's voice rules need to know about each online player, refreshed by the platform on
 * the server thread every few ticks and read from Simple Voice Chat's thread when voices are sent.
 */
public final class ServerPlayers {

    /**
     * One player at the last refresh.
     *
     * @param world     world name (Paper) or dimension id (Fabric, NeoForge)
     * @param regions   WorldGuard regions at the player's position, highest priority first (Paper only)
     * @param mainHand  item id in the main hand ("minecraft:goat_horn"), or "" when empty
     * @param language  the player's client language ("ru_ru"), or "" when unknown
     */
    public record Info(UUID id, String name, String world, double x, double y, double z,
                       boolean sneaking, boolean alive, boolean spectator,
                       String mainHand, String offHand, List<String> regions, String language) {

        public double distanceTo(Info other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        public boolean holds(String itemId) {
            return itemId != null && !itemId.isEmpty() && (itemId.equals(mainHand) || itemId.equals(offHand));
        }
    }

    private final Map<UUID, Info> players = new ConcurrentHashMap<>();

    public void update(Info info) {
        players.put(info.id(), info);
    }

    public void remove(UUID id) {
        players.remove(id);
    }

    public void clear() {
        players.clear();
    }

    /** @return the player, or {@code null} when not online (or not refreshed yet) */
    public Info get(UUID id) {
        return id == null ? null : players.get(id);
    }

    public Collection<Info> all() {
        return players.values();
    }

    /** A player by name, ignoring case. */
    public Info byName(String name) {
        if (name == null) {
            return null;
        }
        String n = name.toLowerCase(Locale.ROOT);
        for (Info i : players.values()) {
            if (i.name() != null && i.name().toLowerCase(Locale.ROOT).equals(n)) {
                return i;
            }
        }
        return null;
    }
}
