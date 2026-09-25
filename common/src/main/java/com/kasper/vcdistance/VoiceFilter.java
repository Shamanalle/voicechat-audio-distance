package com.kasper.vcdistance;

/**
 * Per-stream wall muffling DSP for 48 kHz mono 16-bit voice frames.
 * <p>
 * Two cascaded TPT state-variable low-pass stages (Butterworth damping, 24 dB/oct total) plus a
 * broadband gain. The TPT topology stays stable and click-free while its cutoff is modulated,
 * so parameters are glided every millisecond with a ~90 ms time constant instead of jumping when a
 * speaker steps behind a door frame.
 * <p>
 * When there is nothing to muffle the filter is fully bypassed. Entering and leaving the bypass is
 * crossfaded over one frame, so switching never produces a discontinuity.
 * <p>
 * An instance is not thread-safe: SVC delivers the frames of one channel sequentially.
 */
public final class VoiceFilter {

    public static final int SAMPLE_RATE = 48000;

    private static final int BLOCK = 48;
    private static final double SMOOTHING_SECONDS = 0.09;
    private static final double BLOCK_COEF = 1.0 - Math.exp(-BLOCK / (SMOOTHING_SECONDS * SAMPLE_RATE));
    private static final double DAMPING = Math.sqrt(2.0);

    private static final double MUFFLE_EPSILON = 0.002;
    private static final double LOSS_EPSILON_DB = 0.05;

    private boolean engaged;
    private double muffle;
    private double lossDb;

    private double ic1a;
    private double ic2a;
    private double ic1b;
    private double ic2b;

    /** Smoothed values for UI readouts, written by the audio thread. */
    private volatile float displayMuffle;
    private volatile float displayLossDb;

    /**
     * Processes a frame in place.
     *
     * @param pcm          16-bit mono samples, modified in place
     * @param targetMuffle muffle amount the filter should glide to (0..1)
     * @param targetLossDb broadband loss in dB the filter should glide to (>= 0)
     * @return the same array
     */
    public short[] process(short[] pcm, double targetMuffle, double targetLossDb) {
        if (pcm == null || pcm.length == 0) {
            return pcm;
        }
        targetMuffle = clamp(targetMuffle, 0.0, 1.0);
        targetLossDb = Math.max(0.0, targetLossDb);
        boolean wantsEffect = targetMuffle > MUFFLE_EPSILON || targetLossDb > LOSS_EPSILON_DB;

        if (!engaged) {
            if (!wantsEffect) {
                return pcm;
            }
            engaged = true;
            muffle = 0.0;
            lossDb = 0.0;
            ic1a = ic2a = ic1b = ic2b = 0.0;
            run(pcm, targetMuffle, targetLossDb, 0.0, 1.0);
            return pcm;
        }

        boolean settledOpen = !wantsEffect && muffle < MUFFLE_EPSILON && lossDb < LOSS_EPSILON_DB;
        if (settledOpen) {
            run(pcm, 0.0, 0.0, 1.0, 0.0);
            engaged = false;
            muffle = 0.0;
            lossDb = 0.0;
            displayMuffle = 0.0F;
            displayLossDb = 0.0F;
            return pcm;
        }

        run(pcm, targetMuffle, targetLossDb, 1.0, 1.0);
        return pcm;
    }

    /**
     * @param wetFrom wet/dry mix at the first sample (1 = fully filtered)
     * @param wetTo   wet/dry mix at the last sample
     */
    private void run(short[] pcm, double targetMuffle, double targetLossDb, double wetFrom, double wetTo) {
        int n = pcm.length;
        double wetStep = n > 1 ? (wetTo - wetFrom) / (n - 1) : 0.0;
        double wet = wetFrom;

        double a1 = 0.0;
        double a2 = 0.0;
        double a3 = 0.0;
        double gain = 1.0;

        for (int i = 0; i < n; i++) {
            if (i % BLOCK == 0) {
                muffle += (targetMuffle - muffle) * BLOCK_COEF;
                lossDb += (targetLossDb - lossDb) * BLOCK_COEF;

                double g = Math.tan(Math.PI * OcclusionModel.cutoffHz(muffle) / SAMPLE_RATE);
                a1 = 1.0 / (1.0 + g * (g + DAMPING));
                a2 = g * a1;
                a3 = g * a2;
                gain = OcclusionModel.dbToGain(-lossDb);
            }

            double x = pcm[i];

            double v3 = x - ic2a;
            double v1 = a1 * ic1a + a2 * v3;
            double v2 = ic2a + a2 * ic1a + a3 * v3;
            ic1a = 2.0 * v1 - ic1a;
            ic2a = 2.0 * v2 - ic2a;

            double w3 = v2 - ic2b;
            double w1 = a1 * ic1b + a2 * w3;
            double w2 = ic2b + a2 * ic1b + a3 * w3;
            ic1b = 2.0 * w1 - ic1b;
            ic2b = 2.0 * w2 - ic2b;

            double y = w2 * gain * wet + x * (1.0 - wet);
            wet += wetStep;

            long s = Math.round(y);
            if (s > Short.MAX_VALUE) {
                s = Short.MAX_VALUE;
            } else if (s < Short.MIN_VALUE) {
                s = Short.MIN_VALUE;
            }
            pcm[i] = (short) s;
        }

        displayMuffle = (float) muffle;
        displayLossDb = (float) lossDb;
    }

    public boolean isEngaged() {
        return engaged;
    }

    public float getDisplayMuffle() {
        return displayMuffle;
    }

    public float getDisplayLossDb() {
        return displayLossDb;
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
