package com.kasper.vcdistance;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A place with its own sound profile: a world (dimension) or, on Paper with WorldGuard, a region.
 * Each zone can change how the profile is offered, which preset it uses, or both; whatever it leaves
 * out comes from the server's main profile.
 *
 * @param kind   {@link #WORLD} or {@link #REGION}
 * @param name   world name ("world_nether", or on Fabric "the_nether" / "minecraft:the_nether") or region id
 * @param mode   how the profile is offered here, or {@code null} to keep the server's
 * @param preset preset name here (see {@link ServerSettings#presetByName}), or {@code null} to keep the server's
 */
public record Zone(String kind, String name, ServerSettings.ProfileMode mode, String preset) {

    public static final String WORLD = "world";
    public static final String REGION = "region";

    /** Stable key for tracking which zone a player is in. */
    public String key() {
        return kind + ":" + name;
    }

    /**
     * The zone a player is in: the first of their WorldGuard regions (highest priority first) that has
     * a zone, otherwise their world's zone, otherwise {@code null} (the server's main profile).
     *
     * @param world   the player's world name or dimension id
     * @param regions ids of the regions at the player's position, highest priority first
     */
    public static Zone resolve(Map<String, Zone> zones, String world, List<String> regions) {
        if (zones.isEmpty()) {
            return null;
        }
        if (regions != null) {
            for (String region : regions) {
                Zone z = zones.get(REGION + ":" + normalize(region));
                if (z != null) {
                    return z;
                }
            }
        }
        if (world != null) {
            String w = normalize(world);
            Zone z = zones.get(WORLD + ":" + w);
            if (z == null && w.indexOf(':') >= 0) {
                // "minecraft:the_nether" also matches a zone written as "the_nether"
                z = zones.get(WORLD + ":" + w.substring(w.indexOf(':') + 1));
            }
            return z;
        }
        return null;
    }

    static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
