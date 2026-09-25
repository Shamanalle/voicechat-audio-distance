package com.kasper.vcdistance;

/**
 * Maps the acoustic thickness between speaker and listener to audible parameters.
 * <p>
 * Thickness is measured in "stone blocks" (see {@link AcousticMaterial}). The user strength
 * (0..1) scales it. Two effects are derived, both saturating so that very thick walls
 * converge instead of going silent:
 * <ul>
 *     <li>muffle (0..1) - drives the low-pass cutoff on a logarithmic frequency scale,</li>
 *     <li>loss in dB - broadband transmission loss.</li>
 * </ul>
 * With the default strength (60%) one stone wall gives roughly -8 dB and a ~2.5 kHz cutoff,
 * three walls roughly -18 dB and ~600 Hz.
 */
public final class OcclusionModel {

    public static final double OPEN_CUTOFF_HZ = 16000.0;
    public static final double MIN_CUTOFF_HZ = 350.0;
    public static final double MAX_LOSS_DB = 32.0;

    private static final double MUFFLE_RATE = 1.1;
    private static final double LOSS_RATE = 0.45;

    private OcclusionModel() {
    }

    /**
     * @return muffle amount in [0, 1)
     */
    public static double muffle(double thickness, double strength) {
        double k = effective(thickness, strength);
        return 1.0 - Math.exp(-MUFFLE_RATE * k);
    }

    /**
     * @return broadband loss in dB, in [0, {@link #MAX_LOSS_DB})
     */
    public static double lossDb(double thickness, double strength) {
        double k = effective(thickness, strength);
        return MAX_LOSS_DB * (1.0 - Math.exp(-LOSS_RATE * k));
    }

    /**
     * Low-pass cutoff for a muffle amount, swept exponentially from open to closed.
     */
    public static double cutoffHz(double muffle) {
        double m = Math.max(0.0, Math.min(1.0, muffle));
        return OPEN_CUTOFF_HZ * Math.pow(MIN_CUTOFF_HZ / OPEN_CUTOFF_HZ, m);
    }

    public static double dbToGain(double db) {
        return Math.pow(10.0, db / 20.0);
    }

    private static double effective(double thickness, double strength) {
        if (!(thickness > 0.0) || !(strength > 0.0)) {
            return 0.0;
        }
        return thickness * Math.min(1.0, strength);
    }
}
