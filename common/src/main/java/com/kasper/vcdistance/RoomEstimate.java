package com.kasper.vcdistance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What the space around a point does to a voice, from rays cast in every direction and the blocks
 * they hit.
 * <p>
 * Every ray that escapes into the open is sound lost for good, and every surface swallows a share
 * of what reaches it (stone and ice almost nothing, wool and leaves most of it). From that and the
 * distance to the walls come how long the echo lasts (Eyring's formula, scaled to game-sized
 * rooms), how loud it is and how dull. So a stone hall or a cave rings, a wooden house answers
 * briefly and warmly, a wool room, a forest or an open field stays dry.
 * <p>
 * Nearby walls also give a few distinct early reflections, and far cliffs under the open sky a
 * separate repeat: the echo in the mountains.
 *
 * @param kind            what kind of place it is, for the player to read
 * @param enclosure       share of the rays that hit something within {@link #RAY_LENGTH}, 0 - 1
 * @param meanFree        average distance to the walls that were hit, in blocks
 * @param decaySeconds    how long the echo lasts (RT60)
 * @param wet             echo level before the player's strength setting, 0 - 1
 * @param damping         how much faster the highs of the echo die, 0 - 1
 * @param preDelaySeconds time before the echo builds up
 * @param early           early reflections off the nearest walls
 * @param echoes          distinct repeats off far cliffs (none indoors)
 */
public record RoomEstimate(Kind kind, double enclosure, double meanFree, double decaySeconds, double wet,
                           double damping, double preDelaySeconds, Taps early, Taps echoes) {

    /** What kind of place the listener is in, as the settings screen names it. */
    public enum Kind {
        OPEN("open"),
        FOREST("forest"),
        DEAD("dead"),
        SEMI_OPEN("semi_open"),
        SMALL("small"),
        WOODEN("wooden"),
        TUNNEL("tunnel"),
        ROOM("room"),
        LARGE("large"),
        CANYON("canyon");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public String getTranslationKey() {
            return "gui.vc-audio-distance.effects.space." + id;
        }
    }

    /**
     * Single delayed copies of the voice.
     *
     * @param delays in seconds
     * @param gains  0 - 1, one per delay
     */
    public record Taps(double[] delays, double[] gains) {

        public static final Taps NONE = new Taps(new double[0], new double[0]);

        public int size() {
            return delays.length;
        }

        public boolean isEmpty() {
            return delays.length == 0;
        }

        /** The strongest tap, 0 when there is none. */
        public double loudest() {
            double max = 0.0;
            for (double g : gains) {
                max = Math.max(max, g);
            }
            return max;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Taps t && Arrays.equals(delays, t.delays) && Arrays.equals(gains, t.gains);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(delays) + Arrays.hashCode(gains);
        }
    }

    /**
     * What one ray hit.
     *
     * @param distance in blocks
     * @param material the block's material
     */
    public record Hit(double distance, AcousticMaterial material) {
    }

    public static final RoomEstimate OPEN = new RoomEstimate(Kind.OPEN, 0.0, 0.0, Reverb.MIN_DECAY_SECONDS, 0.0,
            0.35, 0.0, Taps.NONE, Taps.NONE);

    /** Rays for the room; hits further away count as open. */
    public static final double RAY_LENGTH = 32.0;
    /** The horizontal rays look further, for cliffs that give a distinct echo. */
    public static final double ECHO_RAY_LENGTH = 64.0;

    /** Speed of sound in blocks (metres) per second. */
    static final double SOUND_SPEED = 343.0;
    /** Scales Eyring's reverberation time down to what suits game-sized rooms and talking. */
    static final double DECAY_SCALE = 0.015;
    /** Below this the echo is left out: it would only colour the voice. */
    static final double MIN_WET = 0.05;
    /** A cliff this close gives a repeat too quick to hear apart (about 0.1 s). */
    static final double MIN_ECHO_DISTANCE = 17.0;
    static final int MAX_EARLY = 6;
    static final int MAX_ECHOES = 3;

    /** 16 horizontal rays, then the 18 of a cube's corners and edges above and below. */
    public static final double[][] DIRECTIONS = directions();
    /** Horizontal rays: the first 16 of {@link #DIRECTIONS}; every other one also counts for the room. */
    public static final int RING = 16;

    /** How far to trace along {@link #DIRECTIONS}[i]. */
    public static double rayLength(int i) {
        return i < RING ? ECHO_RAY_LENGTH : RAY_LENGTH;
    }

    /**
     * @param hits what the ray along each of {@link #DIRECTIONS} hit, or {@code null} when it hit
     *             nothing within {@link #rayLength}
     */
    public static RoomEstimate of(Hit[] hits) {
        if (hits == null || hits.length != DIRECTIONS.length) {
            return OPEN;
        }
        // The room: 8 horizontal rays and the 18 above and below
        int rays = 0;
        int hit = 0;
        int upMisses = 0;
        int ups = 0;
        boolean sky = false;
        double absorbed = 0.0;
        double distance = 0.0;
        double dampingSum = 0.0;
        int leaves = 0;
        int wood = 0;
        int sideHits = 0;
        for (int i = 0; i < hits.length; i++) {
            if (i < RING && i % 2 != 0) {
                continue;
            }
            rays++;
            Hit h = within(hits[i], RAY_LENGTH);
            boolean up = DIRECTIONS[i][1] > 0.1;
            boolean down = DIRECTIONS[i][1] < -0.1;
            if (up) {
                ups++;
            }
            if (h == null) {
                absorbed += 1.0;
                if (up) {
                    upMisses++;
                    sky |= DIRECTIONS[i][1] > 0.99;
                }
                continue;
            }
            hit++;
            absorbed += h.material().getAbsorption();
            distance += Math.max(0.5, h.distance());
            dampingSum += h.material().getDamping();
            if (!down) {
                sideHits++;
                if (h.material() == AcousticMaterial.LEAVES) {
                    leaves++;
                }
            }
            if (h.material() == AcousticMaterial.WOOD || h.material() == AcousticMaterial.DOOR) {
                wood++;
            }
        }
        double enclosure = (double) hit / rays;
        double alpha = absorbed / rays;
        double meanFree = hit > 0 ? Math.max(1.0, Math.min(RAY_LENGTH, distance / hit)) : 0.0;
        double upOpen = ups > 0 ? (double) upMisses / ups : 1.0;

        double reflect = 1.0 - alpha;
        double wet = hit == 0 ? 0.0 : reflect * reflect * reflect * (0.35 + 0.65 * Math.min(1.0, meanFree / 12.0));
        if (wet < MIN_WET) {
            wet = 0.0;
        }
        double decay = Reverb.MIN_DECAY_SECONDS;
        if (wet > 0.0) {
            // Grows a little slower than the room, so a big hall is long but still lets you talk
            decay = DECAY_SCALE * Math.pow(meanFree, 0.7) / -Math.log(Math.max(0.05, reflect));
        }
        decay = Math.max(Reverb.MIN_DECAY_SECONDS, Math.min(Reverb.MAX_DECAY_SECONDS, decay));
        double damping = hit > 0 ? 0.1 + 0.5 * dampingSum / hit : 0.35;
        double preDelay = wet > 0.0 ? Math.min(0.05, meanFree / SOUND_SPEED) : 0.0;
        Taps early = wet > 0.0 ? early(hits) : Taps.NONE;
        Taps echoes = upOpen >= 0.55 ? echoes(hits) : Taps.NONE;

        Kind kind;
        if (!echoes.isEmpty()) {
            kind = Kind.CANYON;
        } else if (wet <= 0.0) {
            if (sideHits > 0 && leaves >= Math.max(2, sideHits / 3)) {
                kind = Kind.FOREST;
            } else if (enclosure >= 0.8) {
                kind = Kind.DEAD;
            } else {
                kind = Kind.OPEN;
            }
        } else if (sky && upMisses >= 2) {
            kind = Kind.SEMI_OPEN;
        } else if (isTunnel(hits)) {
            kind = Kind.TUNNEL;
        } else if (wood * 2 >= hit) {
            kind = Kind.WOODEN;
        } else if (meanFree < 3.5) {
            kind = Kind.SMALL;
        } else if (meanFree < 9.0) {
            kind = Kind.ROOM;
        } else {
            kind = Kind.LARGE;
        }
        return new RoomEstimate(kind, enclosure, meanFree, decay, Math.min(1.0, wet), damping, preDelay, early, echoes);
    }

    /**
     * The echo a server's zone sets ({@code zone.<kind>.<name>.echo}): a closed stone space whose size
     * grows with {@code size} (0 - 1); 0 is no echo at all.
     */
    public static RoomEstimate forced(double size) {
        double s = Math.max(0.0, Math.min(1.0, size));
        if (s <= 0.0) {
            return OPEN;
        }
        double meanFree = 2.0 + 18.0 * s;
        double decay = Reverb.MIN_DECAY_SECONDS + meanFree * 0.09;
        return new RoomEstimate(s < 0.3 ? Kind.ROOM : Kind.LARGE, 1.0, meanFree,
                Math.min(Reverb.MAX_DECAY_SECONDS, decay), Math.min(1.0, 0.25 + 0.75 * s), 0.2,
                Math.min(0.05, meanFree / SOUND_SPEED), Taps.NONE, Taps.NONE);
    }

    /** Glides towards {@code target} so the echo changes smoothly as the player walks. */
    public RoomEstimate towards(RoomEstimate target, double amount) {
        double a = Math.max(0.0, Math.min(1.0, amount));
        return new RoomEstimate(target.kind,
                enclosure + (target.enclosure - enclosure) * a,
                meanFree + (target.meanFree - meanFree) * a,
                decaySeconds + (target.decaySeconds - decaySeconds) * a,
                wet + (target.wet - wet) * a,
                damping + (target.damping - damping) * a,
                preDelaySeconds + (target.preDelaySeconds - preDelaySeconds) * a,
                target.early, target.echoes);
    }

    /**
     * The echo a voice gets from the listener's space and the speaker's: a friend shouting in a cave
     * echoes even for someone standing outside. {@code speaker} may be {@code null} (same space).
     */
    public static RoomEstimate combine(RoomEstimate listener, RoomEstimate speaker) {
        if (speaker == null || speaker.wet <= 0.0 && speaker.echoes.isEmpty()) {
            return listener;
        }
        if (listener.wet <= 0.0 && listener.echoes.isEmpty()) {
            return speaker;
        }
        double wet = 1.0 - (1.0 - listener.wet) * (1.0 - speaker.wet);
        double total = listener.wet + speaker.wet;
        double s = total > 0.0 ? speaker.wet / total : 0.5;
        RoomEstimate main = s > 0.5 ? speaker : listener;
        return new RoomEstimate(listener.kind,
                Math.max(listener.enclosure, speaker.enclosure),
                listener.meanFree + (speaker.meanFree - listener.meanFree) * s,
                listener.decaySeconds + (speaker.decaySeconds - listener.decaySeconds) * s,
                wet,
                listener.damping + (speaker.damping - listener.damping) * s,
                listener.preDelaySeconds + (speaker.preDelaySeconds - listener.preDelaySeconds) * s,
                main.early,
                listener.echoes.isEmpty() ? speaker.echoes : listener.echoes);
    }

    /**
     * Distance in blocks at which the echo is as loud as the voice itself: grows with the room.
     * Closer voices sound mostly dry, further ones more and more like the room.
     */
    public double criticalDistance() {
        return 1.5 + 0.25 * meanFree;
    }

    /** Share of the echo a voice {@code distance} blocks away gets, 0 - 1 (unknown distance: half). */
    public double distanceShare(double distance) {
        if (!(distance >= 0.0)) {
            return 0.5;
        }
        double dc = criticalDistance();
        return distance * distance / (distance * distance + dc * dc);
    }

    /** {@code true} when there is anything to hear: an echo or a repeat off a cliff. */
    public boolean isAudible() {
        return wet > 0.0 || !echoes.isEmpty();
    }

    private static Hit within(Hit h, double length) {
        return h == null || h.distance() > length ? null : h;
    }

    /** Distinct reflections off the nearest walls and the ceiling (the floor's is in the voice already). */
    private static Taps early(Hit[] hits) {
        List<double[]> list = new ArrayList<>();
        for (int i = 0; i < hits.length; i++) {
            if (i < RING && i % 2 != 0 || DIRECTIONS[i][1] < -0.1) {
                continue;
            }
            Hit h = within(hits[i], RAY_LENGTH);
            if (h == null || h.distance() < 0.8) {
                continue;
            }
            double reflect = 1.0 - h.material().getAbsorption();
            list.add(new double[]{2.0 * h.distance() / SOUND_SPEED, 0.5 * reflect * 2.0 / (2.0 + h.distance())});
        }
        list.sort((a, b) -> Double.compare(a[0], b[0]));
        List<double[]> kept = new ArrayList<>();
        for (double[] t : list) {
            if (kept.size() >= MAX_EARLY) {
                break;
            }
            if (t[1] < 0.03 || !kept.isEmpty() && t[0] - kept.get(kept.size() - 1)[0] < 0.002) {
                continue;
            }
            kept.add(t);
        }
        return taps(kept);
    }

    /**
     * Repeats off far, hard, wide surfaces around a listener under the open sky. A surface counts when
     * two neighbouring horizontal rays hit it at about the same distance (a lone tree trunk does not);
     * walls on opposite sides (a canyon) also send the sound back and forth once more.
     */
    private static Taps echoes(Hit[] hits) {
        double[] dist = new double[RING];
        double[] reflect = new double[RING];
        for (int i = 0; i < RING; i++) {
            Hit h = hits[i];
            dist[i] = -1.0;
            if (h != null && h.distance() >= MIN_ECHO_DISTANCE && h.distance() <= ECHO_RAY_LENGTH && isHard(h.material())) {
                dist[i] = h.distance();
                reflect[i] = 1.0 - h.material().getAbsorption();
            }
        }
        // Neighbouring rays that hit form one surface; its nearest point is where the echo comes back from
        List<double[]> walls = new ArrayList<>();
        int start = 0;
        while (start < RING && dist[start] >= 0.0) {
            start++;
        }
        if (start == RING) {
            // Hard walls all round: one surface
            walls.add(nearest(dist, reflect, 0, RING));
        } else {
            // Starting at a gap, so no surface wraps round the end of the ring
            int k = 0;
            while (k < RING) {
                if (dist[(start + k) % RING] < 0.0) {
                    k++;
                    continue;
                }
                int from = k;
                while (k < RING && dist[(start + k) % RING] >= 0.0) {
                    k++;
                }
                if (k - from >= 2) {
                    walls.add(nearest(dist, reflect, start + from, k - from));
                }
            }
        }
        if (walls.isEmpty()) {
            return Taps.NONE;
        }
        walls.sort((a, b) -> Double.compare(a[0], b[0]));
        List<double[]> kept = new ArrayList<>();
        List<double[]> keptWalls = new ArrayList<>();
        for (double[] w : walls) {
            if (kept.size() >= MAX_ECHOES - 1) {
                break;
            }
            boolean distinct = true;
            for (double[] k : keptWalls) {
                if (Math.abs(k[0] - w[0]) < 6.0) {
                    distinct = false;
                }
            }
            if (!distinct) {
                continue;
            }
            keptWalls.add(w);
            kept.add(new double[]{2.0 * w[0] / SOUND_SPEED, 0.5 * w[1] * Math.sqrt(MIN_ECHO_DISTANCE / w[0])});
        }
        // Walls facing each other: the sound crosses the canyon once more
        if (keptWalls.size() == 2) {
            double[] a = keptWalls.get(0);
            double[] b = keptWalls.get(1);
            int apart = Math.abs((int) a[2] - (int) b[2]);
            apart = Math.min(apart, RING - apart);
            if (apart >= RING / 2 - 1) {
                kept.add(new double[]{kept.get(0)[0] + kept.get(1)[0], 0.6 * kept.get(0)[1] * kept.get(1)[1] * 2.0});
            }
        }
        kept.sort((x, y) -> Double.compare(x[0], y[0]));
        return taps(kept);
    }

    /** The nearest of {@code count} neighbouring ring rays from {@code first}: distance, reflection, ray index. */
    private static double[] nearest(double[] dist, double[] reflect, int first, int count) {
        double[] best = {Double.MAX_VALUE, 0.0, 0.0};
        for (int j = first; j < first + count; j++) {
            int i = j % RING;
            if (dist[i] < best[0]) {
                best = new double[]{dist[i], reflect[i], i};
            }
        }
        return best;
    }

    /** Cliffs, stone walls, ice: surfaces that give a clear echo. Tree trunks and leaves do not. */
    private static boolean isHard(AcousticMaterial m) {
        return m == AcousticMaterial.STONE || m == AcousticMaterial.EARTH || m == AcousticMaterial.ICE
                || m == AcousticMaterial.METAL || m == AcousticMaterial.OTHER || m == AcousticMaterial.GLASS;
    }

    /** Short walls on most sides and a long way along one axis: a corridor, a mine, a tunnel. */
    private static boolean isTunnel(Hit[] hits) {
        int shortSides = 0;
        int longSides = 0;
        for (int i = 0; i < RING; i += 2) {
            Hit h = within(hits[i], RAY_LENGTH);
            if (h == null || h.distance() > 12.0) {
                longSides++;
            } else if (h.distance() <= 4.0) {
                shortSides++;
            }
        }
        return longSides >= 1 && longSides <= 2 && shortSides >= 5;
    }

    private static Taps taps(List<double[]> list) {
        if (list.isEmpty()) {
            return Taps.NONE;
        }
        double[] delays = new double[list.size()];
        double[] gains = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            delays[i] = list.get(i)[0];
            gains[i] = Math.min(1.0, list.get(i)[1]);
        }
        return new Taps(delays, gains);
    }

    private static double[][] directions() {
        List<double[]> d = new ArrayList<>();
        for (int i = 0; i < RING; i++) {
            double a = Math.PI * 2 * i / RING;
            d.add(new double[]{Math.cos(a), 0.0, Math.sin(a)});
        }
        for (int dy = 1; dy >= -1; dy -= 2) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    d.add(new double[]{dx / len, dy / len, dz / len});
                }
            }
        }
        return d.toArray(new double[0][]);
    }
}
