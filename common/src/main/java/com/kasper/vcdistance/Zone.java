package com.kasper.vcdistance;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A place with its own sound: a world (dimension), a box drawn with {@code /vcd zone}, or, on Paper
 * with WorldGuard, a region. A zone can change how the profile is offered, which preset it uses, how
 * far voices carry, how strong walls are and whether there is always an echo; whatever it leaves out
 * comes from the server's main settings.
 *
 * @param kind     {@link #WORLD}, {@link #BOX} or {@link #REGION}
 * @param name     world name ("world_nether", or on Fabric "the_nether" / "minecraft:the_nether"),
 *                 box name or region id
 * @param mode     how the profile is offered here, or {@code null} to keep the server's
 * @param preset   preset name here (see {@link ServerSettings#presetByName}), or {@code null} to keep the server's
 * @param rules    voice range, walls, echo, isolation and the entry message
 * @param box      the box's world and corners (boxes only)
 * @param priority where zones overlap, the highest priority wins
 */
public record Zone(String kind, String name, ServerSettings.ProfileMode mode, String preset,
                   Rules rules, Box box, int priority) {

    public static final String WORLD = "world";
    public static final String BOX = "box";
    public static final String REGION = "region";

    public Zone(String kind, String name, ServerSettings.ProfileMode mode, String preset) {
        this(kind, name, mode, preset, Rules.NONE, null, 0);
    }

    public Zone {
        rules = rules == null ? Rules.NONE : rules;
    }

    /**
     * What a zone does to voices. {@code null} fields keep the server's own behaviour.
     *
     * @param voiceRange      voice range in blocks
     * @param whisperRange    whisper range in blocks
     * @param rangeMultiplier voice and whisper range times this (when no range in blocks is set)
     * @param wallsStrength   wall strength 0 - 1 for players here
     * @param echo            {@code null} = measured as usual, 0 = no echo, above 0 = always this big an echo (0 - 1)
     * @param isolated        voices neither leave nor enter the zone
     * @param enterMessage    shown to players who enter (instead of the zone's name), or {@code null}
     */
    public record Rules(Double voiceRange, Double whisperRange, Double rangeMultiplier, Double wallsStrength,
                        Double echo, boolean isolated, String enterMessage) {

        public static final Rules NONE = new Rules(null, null, null, null, null, false, null);

        public boolean changesRange() {
            return voiceRange != null || whisperRange != null || rangeMultiplier != null || isolated;
        }

        public boolean isEmpty() {
            return equals(NONE);
        }
    }

    /** An axis-aligned box of blocks in one world, both corners included. */
    public record Box(String world, int x1, int y1, int z1, int x2, int y2, int z2) {

        public Box {
            int ax = Math.min(x1, x2), bx = Math.max(x1, x2);
            int ay = Math.min(y1, y2), by = Math.max(y1, y2);
            int az = Math.min(z1, z2), bz = Math.max(z1, z2);
            x1 = ax;
            x2 = bx;
            y1 = ay;
            y2 = by;
            z1 = az;
            z2 = bz;
        }

        public boolean contains(String inWorld, double x, double y, double z) {
            return inWorld != null && sameWorld(world, inWorld)
                    && x >= x1 && x < x2 + 1 && y >= y1 && y < y2 + 1 && z >= z1 && z < z2 + 1;
        }

        public long volume() {
            return (long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
        }

        /** "x1,y1,z1" and "x2,y2,z2", as written to the settings file. */
        public String from() {
            return x1 + "," + y1 + "," + z1;
        }

        public String to() {
            return x2 + "," + y2 + "," + z2;
        }
    }

    /** Stable key for tracking which zone a player is in. */
    public String key() {
        return kind + ":" + name;
    }

    /**
     * The zone a player is in, without a position (boxes are skipped): see
     * {@link #resolve(Map, String, List, double, double, double)}.
     */
    public static Zone resolve(Map<String, Zone> zones, String world, List<String> regions) {
        return resolve(zones, world, regions, Double.NaN, Double.NaN, Double.NaN);
    }

    /**
     * The zone a player is in: of their WorldGuard regions and the boxes around them, the one with
     * the highest priority (on a tie: regions in WorldGuard's order, then the smallest box);
     * otherwise their world's zone; otherwise {@code null} (the server's main settings).
     *
     * @param regions ids of the regions at the player's position, highest priority first
     */
    public static Zone resolve(Map<String, Zone> zones, String world, List<String> regions, double x, double y, double z) {
        if (zones.isEmpty()) {
            return null;
        }
        Zone best = null;
        if (regions != null) {
            for (String region : regions) {
                Zone r = zones.get(REGION + ":" + normalize(region));
                if (r != null && (best == null || r.priority() > best.priority())) {
                    best = r;
                }
            }
        }
        if (!Double.isNaN(x)) {
            for (Zone b : zones.values()) {
                if (b.box() == null || !b.box().contains(world, x, y, z)) {
                    continue;
                }
                if (best == null || b.priority() > best.priority()
                        || (b.priority() == best.priority() && best.box() != null && b.box().volume() < best.box().volume())) {
                    best = b;
                }
            }
        }
        if (best != null) {
            return best;
        }
        if (world != null) {
            String w = normalize(world);
            Zone inWorld = zones.get(WORLD + ":" + w);
            if (inWorld == null && w.indexOf(':') >= 0) {
                // "minecraft:the_nether" also matches a zone written as "the_nether"
                inWorld = zones.get(WORLD + ":" + w.substring(w.indexOf(':') + 1));
            }
            return inWorld;
        }
        return null;
    }

    /** "minecraft:the_nether" and "the_nether" are the same world. */
    static boolean sameWorld(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        return x.equals(y) || strip(x).equals(strip(y));
    }

    private static String strip(String world) {
        return world.startsWith("minecraft:") ? world.substring("minecraft:".length()) : world;
    }

    static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
