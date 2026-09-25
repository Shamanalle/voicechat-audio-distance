package com.kasper.vcdistance;

/**
 * Pure mathematical models and audio physics for distance attenuation,
 * volume falloff curves, and acoustic propagation.
 */
public final class AudioPhysics {

    private AudioPhysics() {
    }

    /**
     * Calculates the normalized gain (0.0 - 1.0) according to OpenAL physical attenuation formulas.
     *
     * @param distFraction Normalized distance fraction (0.0 = listener, 1.0 = max distance)
     * @param model        OpenAL attenuation model (Linear, Realistic Inverse, Exponential)
     * @param rolloff      Rolloff factor (attenuation slope multiplier)
     * @param minVol       Hardware minimum volume floor (AL_MIN_GAIN)
     * @param refRatio     Reference distance ratio (volume remains 1.0 inside this zone)
     * @return Normalized gain clamped between minVol and 1.0
     */
    public static double calculateGain(double distFraction, AttenuationModel model,
                                      double rolloff, double minVol, double refRatio) {
        double gain;
        if (distFraction <= refRatio) {
            gain = 1.0;
        } else {
            double excess = distFraction - refRatio;
            double remaining = Math.max(0.001, 1.0 - refRatio);

            switch (model) {
                case REALISTIC_INVERSE -> {
                    gain = refRatio / (refRatio + rolloff * excess);
                }
                case EXPONENTIAL -> {
                    gain = Math.pow(Math.max(0.0001, distFraction / Math.max(0.01, refRatio)), -rolloff);
                }
                default -> {
                    gain = 1.0 - rolloff * (excess / remaining);
                }
            }
        }
        return Math.max(minVol, Math.max(0.0, Math.min(1.0, gain)));
    }
}
