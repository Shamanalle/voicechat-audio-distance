package com.kasper.vcdistance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Which zone each player with the addon is in, so the server sends a new profile only when it changes. */
public final class ZoneTracker {

    private static final String NO_ZONE = "";

    private final Map<UUID, String> current = new ConcurrentHashMap<>();

    /** Records the zone the player was just sent. */
    public void set(UUID player, Zone zone) {
        current.put(player, zone == null ? NO_ZONE : zone.key());
    }

    /** @return {@code true} when the player is now in a different zone than last recorded */
    public boolean changed(UUID player, Zone zone) {
        String key = zone == null ? NO_ZONE : zone.key();
        String previous = current.put(player, key);
        return !key.equals(previous);
    }

    public void forget(UUID player) {
        current.remove(player);
    }

    public void clear() {
        current.clear();
    }
}
