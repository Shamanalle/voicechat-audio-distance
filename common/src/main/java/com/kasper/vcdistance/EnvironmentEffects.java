package com.kasper.vcdistance;

/**
 * Water and weather on top of the walls: under water, voices are dull and quiet; in rain, far voices
 * drown in the noise. Both come out as muffle and loss, combined with the wall muffling.
 */
public final class EnvironmentEffects {

    /** Under water: about 600 Hz and -10 dB. */
    static final double WATER_MUFFLE = 0.85;
    static final double WATER_LOSS_DB = 10.0;
    /** Rain at the edge of the range; thunderstorms are 1.6 times as loud. */
    static final double RAIN_LOSS_DB = 6.0;
    static final double RAIN_MUFFLE = 0.15;
    static final double THUNDER_FACTOR = 1.6;

    /** Weather at the listener or speaker. */
    public enum Weather {
        CLEAR,
        RAIN,
        THUNDER
    }

    /** Muffle (0 - 1) and loss in dB. */
    public record Effect(double muffle, double lossDb) {

        public static final Effect NONE = new Effect(0.0, 0.0);

        /** Both at once: muffle combines like two filters in a row, losses add up. */
        public Effect plus(Effect other) {
            return new Effect(1.0 - (1.0 - muffle) * (1.0 - other.muffle), lossDb + other.lossDb);
        }
    }

    private EnvironmentEffects() {
    }

    /**
     * @param listenerUnderWater the listener's head is under water
     * @param speakerUnderWater  the speaker's head is under water
     */
    public static Effect water(boolean listenerUnderWater, boolean speakerUnderWater) {
        return listenerUnderWater || speakerUnderWater ? new Effect(WATER_MUFFLE, WATER_LOSS_DB) : Effect.NONE;
    }

    /**
     * @param weather      rain where the listener or the speaker stands under the open sky
     * @param distFraction distance as a share of the voice range: rain covers far voices more
     */
    public static Effect weather(Weather weather, double distFraction) {
        if (weather == null || weather == Weather.CLEAR) {
            return Effect.NONE;
        }
        double f = Math.max(0.0, Math.min(1.0, distFraction));
        double k = weather == Weather.THUNDER ? THUNDER_FACTOR : 1.0;
        return new Effect(Math.min(1.0, RAIN_MUFFLE * k * f), RAIN_LOSS_DB * k * f);
    }
}
