package com.kasper.vcdistance;

/**
 * Room echo for one 48 kHz mono voice stream: a small Schroeder / Freeverb-style reverb (four
 * damped comb filters in parallel, then two all-pass filters). Only the wet part is added to the
 * voice, so with no echo the audio is untouched.
 * <p>
 * Parameters glide once per frame, and the filter keeps running until its tail has died away after
 * the echo is turned down, so there are no clicks. Not thread-safe: one instance per stream, which
 * Simple Voice Chat delivers sequentially.
 */
public final class Reverb {

    /** Freeverb's comb and all-pass lengths (tuned for 44.1 kHz), scaled to 48 kHz. */
    private static final int[] COMB_LENGTHS = scale(1116, 1188, 1277, 1356);
    private static final int[] ALLPASS_LENGTHS = scale(556, 441);
    private static final double ALLPASS_FEEDBACK = 0.5;
    /** High frequencies die faster than lows, as on real walls. */
    private static final double DAMPING = 0.35;
    /** Keeps the summed combs from clipping. */
    private static final double INPUT_GAIN = 0.12;
    private static final double GLIDE = 0.25;
    private static final double SILENT = 1e-3;

    public static final double MIN_DECAY_SECONDS = 0.15;
    public static final double MAX_DECAY_SECONDS = 4.0;

    private final double[][] combs = new double[COMB_LENGTHS.length][];
    private final int[] combPos = new int[COMB_LENGTHS.length];
    private final double[] combLow = new double[COMB_LENGTHS.length];
    private final double[][] allpasses = new double[ALLPASS_LENGTHS.length][];
    private final int[] allpassPos = new int[ALLPASS_LENGTHS.length];

    private double wet;
    private double decay = 1.0;
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
     * Adds the echo to a frame in place.
     *
     * @param pcm          16-bit mono samples
     * @param targetWet    echo level, 0 - 1 (0 = off)
     * @param decaySeconds how long the echo takes to fade by 60 dB
     * @return the same array
     */
    public short[] process(short[] pcm, double targetWet, double decaySeconds) {
        if (pcm == null || pcm.length == 0) {
            return pcm;
        }
        targetWet = Math.max(0.0, Math.min(1.0, targetWet));
        decaySeconds = Math.max(MIN_DECAY_SECONDS, Math.min(MAX_DECAY_SECONDS, decaySeconds));
        if (!active && targetWet < SILENT) {
            return pcm;
        }
        if (!active) {
            clear();
            active = true;
            wet = 0.0;
        }
        double wetFrom = wet;
        wet += (targetWet - wet) * GLIDE;
        decay += (decaySeconds - decay) * GLIDE;
        double wetStep = (wet - wetFrom) / pcm.length;

        double[] feedback = new double[combs.length];
        for (int c = 0; c < combs.length; c++) {
            feedback[c] = Math.pow(10.0, -3.0 * COMB_LENGTHS[c] / (decay * VoiceFilter.SAMPLE_RATE));
        }

        double energy = 0.0;
        double w = wetFrom;
        for (int i = 0; i < pcm.length; i++) {
            double x = pcm[i] * INPUT_GAIN;
            double sum = 0.0;
            for (int c = 0; c < combs.length; c++) {
                double[] buf = combs[c];
                int p = combPos[c];
                double out = buf[p];
                combLow[c] = out * (1.0 - DAMPING) + combLow[c] * DAMPING;
                buf[p] = x + combLow[c] * feedback[c];
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
            energy += sum * sum;
            w += wetStep;
            long y = Math.round(pcm[i] + sum * w);
            pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, y));
        }
        // Turned down and the tail has died away: stop until the echo is wanted again
        if (targetWet < SILENT && (wet < SILENT || Math.sqrt(energy / pcm.length) < 1.0)) {
            active = false;
            wet = 0.0;
        }
        return pcm;
    }

    public boolean isActive() {
        return active;
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
    }

    private static int[] scale(int... lengths) {
        int[] out = new int[lengths.length];
        for (int i = 0; i < lengths.length; i++) {
            out[i] = (int) Math.round(lengths[i] * VoiceFilter.SAMPLE_RATE / 44100.0);
        }
        return out;
    }
}
