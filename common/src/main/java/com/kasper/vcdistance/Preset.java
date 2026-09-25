package com.kasper.vcdistance;

/**
 * One-click sound profiles. Presets change the distance curve and the wall muffling;
 * per-material weights are left untouched.
 */
public enum Preset {

    /** Exactly what Simple Voice Chat does without the addon. */
    VANILLA("default", AttenuationModel.LINEAR, 1.00, 0.00, 0.50, 1.00, false, 0.60),
    /** Natural 1/r falloff with a soft floor and wall muffling. */
    REALISTIC("realistic", AttenuationModel.REALISTIC_INVERSE, 0.70, 0.05, 0.60, 1.10, true, 0.70),
    /** Everyone stays understandable: gentle falloff, high floor, no walls. */
    CLEAR("high_audibility", AttenuationModel.LINEAR, 0.35, 0.25, 0.80, 0.90, false, 0.30),
    /** Short, tense range for horror and stealth maps. */
    ATMOSPHERIC("atmospheric", AttenuationModel.EXPONENTIAL, 1.00, 0.00, 0.35, 1.40, true, 0.85);

    private static final double EPSILON = 0.005;

    private final String id;
    private final AttenuationModel model;
    private final double rolloff;
    private final double minVolume;
    private final double reference;
    private final double whisper;
    private final boolean occlusion;
    private final double strength;

    Preset(String id, AttenuationModel model, double rolloff, double minVolume, double reference,
           double whisper, boolean occlusion, double strength) {
        this.id = id;
        this.model = model;
        this.rolloff = rolloff;
        this.minVolume = minVolume;
        this.reference = reference;
        this.whisper = whisper;
        this.occlusion = occlusion;
        this.strength = strength;
    }

    public String getId() {
        return id;
    }

    public String getTranslationKey() {
        return "gui.vc-audio-distance.preset." + id;
    }

    public String getTooltipKey() {
        return "gui.vc-audio-distance.preset." + id + ".tooltip";
    }

    public void apply(DistanceConfig config) {
        config.setModel(model);
        config.setAttenuationFactor(rolloff);
        config.setMinVolumeFraction(minVolume);
        config.setOpenalReferenceRatio(reference);
        config.setWhisperMultiplier(whisper);
        config.setOcclusionEnabled(occlusion);
        config.setOcclusionStrength(strength);
    }

    /** True when the config currently sounds like this preset (wall strength only matters when walls are on). */
    public boolean matches(DistanceConfig config) {
        if (config.getModel() != model
                || !near(config.getAttenuationFactor(), rolloff)
                || !near(config.getMinVolumeFraction(), minVolume)
                || !near(config.getOpenalReferenceRatio(), reference)
                || !near(config.getWhisperMultiplier(), whisper)
                || config.isOcclusionEnabled() != occlusion) {
            return false;
        }
        return !occlusion || near(config.getOcclusionStrength(), strength);
    }

    public static Preset find(DistanceConfig config) {
        for (Preset p : values()) {
            if (p.matches(config)) {
                return p;
            }
        }
        return null;
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < EPSILON;
    }
}
