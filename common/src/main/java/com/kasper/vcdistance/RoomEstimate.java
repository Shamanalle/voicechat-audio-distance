package com.kasper.vcdistance;

/**
 * How echoey the space around the listener is, from rays cast in every direction: the more of them
 * hit a wall, the more enclosed it is, and the further away the walls are, the longer the echo.
 * A small room is enclosed but echoes little; a big cave or hall echoes long; the open air not at all.
 *
 * @param enclosure   share of rays that hit something, 0 - 1
 * @param meanFree    average distance to the walls in blocks (misses count as the ray length)
 * @param decaySeconds how long an echo lasts (RT60)
 * @param wet         echo level before the player's strength setting, 0 - 1
 */
public record RoomEstimate(double enclosure, double meanFree, double decaySeconds, double wet) {

    public static final RoomEstimate OPEN = new RoomEstimate(0.0, 0.0, Reverb.MIN_DECAY_SECONDS, 0.0);

    /** Rays at least this long; shorter hits count for a small room. */
    public static final double RAY_LENGTH = 32.0;

    /** Directions to trace: 8 around, 4 up and 4 down at 45 degrees, straight up and down. */
    public static final double[][] DIRECTIONS = directions();

    /**
     * @param hits distance to the first solid block along each of {@link #DIRECTIONS}, or a
     *             negative value when the ray hit nothing within {@link #RAY_LENGTH}
     */
    public static RoomEstimate of(double[] hits) {
        if (hits == null || hits.length == 0) {
            return OPEN;
        }
        int hit = 0;
        double total = 0.0;
        for (double d : hits) {
            if (d >= 0.0) {
                hit++;
                total += Math.min(d, RAY_LENGTH);
            } else {
                total += RAY_LENGTH;
            }
        }
        double enclosure = (double) hit / hits.length;
        double meanFree = total / hits.length;
        // Echo needs walls on most sides; a roof alone or a single wall adds little
        double closed = Math.max(0.0, (enclosure - 0.35) / 0.65);
        double size = Math.min(1.0, meanFree / 14.0);
        double wet = closed * closed * (0.25 + 0.75 * size);
        double decay = Reverb.MIN_DECAY_SECONDS + meanFree * 0.09 * (0.4 + 0.6 * closed);
        return new RoomEstimate(enclosure, meanFree, Math.min(Reverb.MAX_DECAY_SECONDS, decay), Math.min(1.0, wet));
    }

    /** Glides towards {@code target} so the echo changes smoothly as the player walks. */
    public RoomEstimate towards(RoomEstimate target, double amount) {
        double a = Math.max(0.0, Math.min(1.0, amount));
        return new RoomEstimate(
                enclosure + (target.enclosure - enclosure) * a,
                meanFree + (target.meanFree - meanFree) * a,
                decaySeconds + (target.decaySeconds - decaySeconds) * a,
                wet + (target.wet - wet) * a);
    }

    private static double[][] directions() {
        double[][] d = new double[18][];
        int n = 0;
        double s = Math.sqrt(0.5);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            d[n++] = new double[]{Math.cos(a), 0.0, Math.sin(a)};
        }
        for (int i = 0; i < 4; i++) {
            double a = Math.PI * 2 * i / 4 + Math.PI / 4;
            d[n++] = new double[]{Math.cos(a) * s, s, Math.sin(a) * s};
            d[n++] = new double[]{Math.cos(a) * s, -s, Math.sin(a) * s};
        }
        d[n++] = new double[]{0.0, 1.0, 0.0};
        d[n] = new double[]{0.0, -1.0, 0.0};
        return d;
    }
}
