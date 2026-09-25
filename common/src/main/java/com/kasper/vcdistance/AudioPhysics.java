package com.kasper.vcdistance;

/**
 * The distance curves. The same function draws the graph and sets the volume of every voice
 * (see {@code AudioDistancePlugin.onOpenALSound}), so what the screen shows is what you hear.
 * <p>
 * Distances are fractions of the hearing range: 0 is the listener, 1 is where Simple Voice Chat
 * stops sending the voice. Up to {@code refRatio} the voice is at full volume; beyond it
 * {@code x} runs from 0 to 1 over the rest of the range. Every curve reaches silence at the edge
 * when the falloff is 100%, so voices fade out instead of being cut off there.
 */
public final class AudioPhysics {

    /**
     * Steepness of the exponential curve: e^(-4.6 x) is 1% at the edge before normalising, so the
     * voice loses most of its loudness in the first third past the full-volume zone.
     */
    static final double EXPONENTIAL_STEEPNESS = 4.6;

    /** The 1/r curve fades to silence over this last part of the range (as a fraction of x). */
    static final double INVERSE_FADE = 0.25;

    private AudioPhysics() {
    }

    /**
     * Gain (0 - 1) of a voice at {@code distFraction} of the hearing range.
     *
     * @param distFraction distance as a fraction of the hearing range (0 = listener, 1 = edge)
     * @param model        curve
     * @param rolloff      falloff: 0 = full volume everywhere, 1 = the full curve, above 1
     *                     (whispers) = steeper, reaching silence before the edge
     * @param minVol       volume floor ("edge volume")
     * @param refRatio     fraction of the range heard at full volume
     * @return gain between {@code minVol} and 1
     */
    public static double calculateGain(double distFraction, AttenuationModel model,
                                       double rolloff, double minVol, double refRatio) {
        double gain;
        if (distFraction <= refRatio) {
            gain = 1.0;
        } else {
            double remaining = Math.max(0.001, 1.0 - refRatio);
            double x = Math.min(1.0, (distFraction - refRatio) / remaining);
            gain = switch (model) {
                case REALISTIC_INVERSE -> inverse(distFraction, refRatio, rolloff, x);
                case EXPONENTIAL -> 1.0 - rolloff * (1.0 - exponential(x));
                default -> 1.0 - rolloff * x; // Simple Voice Chat's own linear curve
            };
        }
        return Math.max(minVol, Math.max(0.0, Math.min(1.0, gain)));
    }

    /** Exponential decay from 1 at x = 0 to exactly 0 at x = 1. */
    static double exponential(double x) {
        double edge = Math.exp(-EXPONENTIAL_STEEPNESS);
        return (Math.exp(-EXPONENTIAL_STEEPNESS * x) - edge) / (1.0 - edge);
    }

    /**
     * Sound in the open: loudness falls as 1/r (zone / distance at 100% falloff), then fades to
     * silence over the last quarter of the range instead of stopping at the edge.
     */
    static double inverse(double distFraction, double refRatio, double rolloff, double x) {
        double ref = Math.max(0.001, refRatio);
        double gain = ref / (ref + rolloff * (distFraction - refRatio));
        double fade = 1.0;
        if (x > 1.0 - INVERSE_FADE) {
            double t = (x - (1.0 - INVERSE_FADE)) / INVERSE_FADE;
            fade = 0.5 * (1.0 + Math.cos(Math.PI * t));
        }
        // With less than full falloff the fade is partial too, so 0% falloff stays flat
        double depth = Math.min(1.0, rolloff);
        return gain * (1.0 - depth * (1.0 - fade));
    }
}
