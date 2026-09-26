package com.kasper.vcdistance;

/**
 * Echo for one 48 kHz mono voice stream, shaped by a {@link RoomEstimate}:
 * <ul>
 *     <li>a few early reflections off the nearest walls (single delayed copies),</li>
 *     <li>the room's tail: a Freeverb-style reverb (eight damped comb filters in parallel, then four
 *     all-pass filters) after a short pre-delay, whose highs die faster in soft or wooden spaces,</li>
 *     <li>distinct repeats off far cliffs: the echo in the mountains.</li>
 * </ul>
 * Only the echo is added to the voice, so with none the audio is untouched. Levels glide once per
 * frame and changed delays crossfade over one frame, so nothing clicks; after the echo is turned
 * down the filter keeps running until its tail has died away. Not thread-safe: one instance per
 * stream, which Simple Voice Chat delivers sequentially.
 */
public final class Reverb {

    /** Freeverb's comb and all-pass lengths (tuned for 44.1 kHz), scaled to 48 kHz. */
    private static final int[] COMB_LENGTHS = scale(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617);
    private static final int[] ALLPASS_LENGTHS = scale(556, 441, 341, 225);
    private static final double ALLPASS_FEEDBACK = 0.5;
    /** Keeps the summed combs from clipping. */
    private static final double INPUT_GAIN = 0.035;
    /** Early reflections next to the tail. */
    private static final double EARLY_GAIN = 0.6;
    private static final double GLIDE = 0.25;
    private static final double SILENT = 1e-3;
    /** Longest delay kept: repeats off cliffs 64 blocks away, and once more across a canyon. */
    private static final int HISTORY = (int) (1.2 * VoiceFilter.SAMPLE_RATE);

    public static final double MIN_DECAY_SECONDS = 0.15;
    public static final double MAX_DECAY_SECONDS = 4.0;

    /** A plain echo with the default damping and no reflections, for the forced zone echo and tests. */
    private static final double DEFAULT_DAMPING = 0.35;

    private final double[][] combs = new double[COMB_LENGTHS.length][];
    private final int[] combPos = new int[COMB_LENGTHS.length];
    private final double[] combLow = new double[COMB_LENGTHS.length];
    private final double[][] allpasses = new double[ALLPASS_LENGTHS.length][];
    private final int[] allpassPos = new int[ALLPASS_LENGTHS.length];
    private float[] history;
    private int historyPos;

    private double wet;
    private double echo;
    private double decay = 1.0;
    private double damping = DEFAULT_DAMPING;
    private int preDelay;
    private int[] earlyDelays = new int[0];
    private double[] earlyGains = new double[0];
    private int[] echoDelays = new int[0];
    private double[] echoGains = new double[0];
    private boolean active;

    public Reverb() {
        for (int i = 0; i < combs.length; i++) {
            combs[i] = new double[COMB_LENGTHS[i]];
        }
        for (int i = 0; i < allpasses.length; i++) {
            allpasses[i] = new double[ALLPASS_LENGTHS[i]];
        }
    }

    /**
     * A plain room echo with no reflections or repeats.
     *
     * @param targetWet    echo level, 0 - 1 (0 = off)
     * @param decaySeconds how long the echo takes to fade by 60 dB
     */
    public short[] process(short[] pcm, double targetWet, double decaySeconds) {
        return process(pcm, new RoomEstimate(RoomEstimate.Kind.ROOM, 1.0, 0.0, decaySeconds, 1.0, DEFAULT_DAMPING,
                0.0, RoomEstimate.Taps.NONE, RoomEstimate.Taps.NONE), targetWet, 0.0);
    }

    /**
     * Adds the echo to a frame in place.
     *
     * @param pcm        16-bit mono samples
     * @param room       the space the voice echoes in
     * @param targetWet  level of the room's echo and reflections, 0 - 1 (0 = off)
     * @param targetEcho level of the repeats off far cliffs, 0 - 1 (0 = off)
     * @return the same array
     */
    public short[] process(short[] pcm, RoomEstimate room, double targetWet, double targetEcho) {
        if (pcm == null || pcm.length == 0 || room == null) {
            return pcm;
        }
        targetWet = clamp(targetWet);
        targetEcho = room.echoes().isEmpty() ? 0.0 : clamp(targetEcho);
        double decaySeconds = Math.max(MIN_DECAY_SECONDS, Math.min(MAX_DECAY_SECONDS, room.decaySeconds()));
        if (!active && targetWet < SILENT && targetEcho < SILENT) {
            return pcm;
        }
        int[] newEarly = samples(room.early().delays());
        int[] newEcho = samples(room.echoes().delays());
        int newPre = (int) Math.round(Math.max(0.0, room.preDelaySeconds()) * VoiceFilter.SAMPLE_RATE);
        if (!active) {
            clear();
            active = true;
            wet = 0.0;
            echo = 0.0;
            decay = decaySeconds;
            damping = room.damping();
            preDelay = newPre;
            earlyDelays = newEarly;
            earlyGains = room.early().gains();
            echoDelays = newEcho;
            echoGains = room.echoes().gains();
        }
        // Changed delays: this frame fades from the old ones to the new ones
        int[] oldEarly = earlyDelays;
        double[] oldEarlyGains = earlyGains;
        int[] oldEcho = echoDelays;
        double[] oldEchoGains = echoGains;
        int oldPre = preDelay;
        boolean fade = oldPre != newPre || !java.util.Arrays.equals(oldEarly, newEarly)
                || !java.util.Arrays.equals(oldEcho, newEcho)
                || !java.util.Arrays.equals(oldEarlyGains, room.early().gains())
                || !java.util.Arrays.equals(oldEchoGains, room.echoes().gains());
        earlyDelays = newEarly;
        earlyGains = room.early().gains();
        echoDelays = newEcho;
        echoGains = room.echoes().gains();
        preDelay = newPre;

        double wetFrom = wet;
        double echoFrom = echo;
        wet += (targetWet - wet) * GLIDE;
        echo += (targetEcho - echo) * GLIDE;
        decay += (decaySeconds - decay) * GLIDE;
        damping += (Math.max(0.0, Math.min(0.9, room.damping())) - damping) * GLIDE;
        int n = pcm.length;
        double wetStep = (wet - wetFrom) / n;
        double echoStep = (echo - echoFrom) / n;

        double[] feedback = new double[combs.length];
        for (int c = 0; c < combs.length; c++) {
            feedback[c] = Math.pow(10.0, -3.0 * COMB_LENGTHS[c] / (decay * VoiceFilter.SAMPLE_RATE));
        }

        double energy = 0.0;
        double w = wetFrom;
        double e = echoFrom;
        for (int i = 0; i < n; i++) {
            double x = pcm[i];
            history[historyPos] = (float) x;
            double t = fade ? (i + 1.0) / n : 1.0;

            // The tail, fed after the pre-delay
            double in = read(preDelay);
            if (fade && oldPre != preDelay) {
                in = read(oldPre) * (1.0 - t) + in * t;
            }
            in *= INPUT_GAIN;
            double sum = 0.0;
            for (int c = 0; c < combs.length; c++) {
                double[] buf = combs[c];
                int p = combPos[c];
                double out = buf[p];
                combLow[c] = out * (1.0 - damping) + combLow[c] * damping;
                buf[p] = in + combLow[c] * feedback[c];
                combPos[c] = p + 1 == buf.length ? 0 : p + 1;
                sum += out;
            }
            for (int a = 0; a < allpasses.length; a++) {
                double[] buf = allpasses[a];
                int p = allpassPos[a];
                double stored = buf[p];
                double out = stored - sum;
                buf[p] = sum + stored * ALLPASS_FEEDBACK;
                allpassPos[a] = p + 1 == buf.length ? 0 : p + 1;
                sum = out;
            }

            double early = taps(earlyDelays, earlyGains);
            double repeats = taps(echoDelays, echoGains);
            if (fade) {
                early = taps(oldEarly, oldEarlyGains) * (1.0 - t) + early * t;
                repeats = taps(oldEcho, oldEchoGains) * (1.0 - t) + repeats * t;
            }

            w += wetStep;
            e += echoStep;
            double added = w * (sum + EARLY_GAIN * early) + e * repeats;
            energy += added * added;
            long y = Math.round(x + added);
            pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, y));
            historyPos = historyPos + 1 == HISTORY ? 0 : historyPos + 1;
        }
        // Turned down and the tail has died away: stop until the echo is wanted again
        boolean quiet = wet < SILENT && echo < SILENT || Math.sqrt(energy / n) < 1.0;
        if (targetWet < SILENT && targetEcho < SILENT && quiet) {
            active = false;
            wet = 0.0;
            echo = 0.0;
        }
        return pcm;
    }

    public boolean isActive() {
        return active;
    }

    /** The sample {@code delay} samples before the current one. */
    private double read(int delay) {
        int d = Math.max(0, Math.min(HISTORY - 1, delay));
        int p = historyPos - d;
        return history[p < 0 ? p + HISTORY : p];
    }

    private double taps(int[] delays, double[] gains) {
        double sum = 0.0;
        for (int k = 0; k < delays.length; k++) {
            sum += read(delays[k]) * gains[k];
        }
        return sum;
    }

    private void clear() {
        for (int c = 0; c < combs.length; c++) {
            java.util.Arrays.fill(combs[c], 0.0);
            combPos[c] = 0;
            combLow[c] = 0.0;
        }
        for (int a = 0; a < allpasses.length; a++) {
            java.util.Arrays.fill(allpasses[a], 0.0);
            allpassPos[a] = 0;
        }
        if (history == null) {
            history = new float[HISTORY];
        } else {
            java.util.Arrays.fill(history, 0.0F);
        }
        historyPos = 0;
    }

    private static int[] samples(double[] seconds) {
        int[] out = new int[seconds.length];
        for (int i = 0; i < seconds.length; i++) {
            out[i] = (int) Math.round(Math.max(0.0, seconds[i]) * VoiceFilter.SAMPLE_RATE);
        }
        return out;
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static int[] scale(int... lengths) {
        int[] out = new int[lengths.length];
        for (int i = 0; i < lengths.length; i++) {
            out[i] = (int) Math.round(lengths[i] * VoiceFilter.SAMPLE_RATE / 44100.0);
        }
        return out;
    }
}
