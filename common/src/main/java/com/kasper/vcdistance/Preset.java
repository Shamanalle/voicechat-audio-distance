package com.kasper.vcdistance;

/**
 * One-click sound profiles. Presets change the distance curve and the wall muffling;
 * per-material weights are left untouched.
 */
public enum Preset {

    /** Exactly what Simple Voice Chat does without the addon: full volume over half the range. */
    VANILLA("default", AttenuationModel.LINEAR, 1.00, 0.00, FullVolume.share(0.50), 1.00, false, 0.60),
    /**
     * Natural 1/r falloff with a soft floor and wall muffling. Loudness depends on the distance in
     * blocks, as in real life, so full volume ends about 12 blocks away whatever the server's range.
     */
    REALISTIC("realistic", AttenuationModel.REALISTIC_INVERSE, 0.70, 0.05, FullVolume.blocks(12, 0.05, 0.40), 1.10, true, 0.70),
    /** Everyone stays understandable: gentle falloff, high floor, no walls. */
    CLEAR("high_audibility", AttenuationModel.LINEAR, 0.35, 0.25, FullVolume.blocks(24, 0.30, 0.80), 0.90, false, 0.30),
    /** Short, tense range for horror and stealth maps: only people right next to you are loud. */
    ATMOSPHERIC("atmospheric", AttenuationModel.EXPONENTIAL, 1.00, 0.00, FullVolume.blocks(7, 0.05, 0.30), 1.40, true, 0.85);

    /**
     * The full-volume zone of a preset: a share of the server's voice range, or a distance in blocks
     * kept within {@code minShare} - {@code maxShare} of the range, so it fits small and large ranges.
     */
    record FullVolume(double blocks, double minShare, double maxShare) {

        static FullVolume share(double share) {
            return new FullVolume(Double.NaN, share, share);
        }

        static FullVolume blocks(double blocks, double minShare, double maxShare) {
            return new FullVolume(blocks, minShare, maxShare);
        }

        /** Full-volume zone as a share of {@code range} blocks. */
        double shareOf(double range) {
            if (Double.isNaN(blocks) || range <= 0.0) {
                return minShare;
            }
            return DistanceConfig.clamp(blocks / range, minShare, maxShare);
        }
    }

    private static final double EPSILON = 0.005;

    private final String id;
    private final AttenuationModel model;
    private final double rolloff;
    private final double minVolume;
    private final FullVolume zone;
    private final double whisper;
    private final boolean occlusion;
    private final double strength;

    Preset(String id, AttenuationModel model, double rolloff, double minVolume, FullVolume zone,
           double whisper, boolean occlusion, double strength) {
        this.id = id;
        this.model = model;
        this.rolloff = rolloff;
        this.minVolume = minVolume;
        this.zone = zone;
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

    /** Whether the preset's values depend on the voice range (its full-volume zone is in blocks). */
    public boolean dependsOnRange() {
        return !Double.isNaN(zone.blocks());
    }

    /** Full-volume zone this preset uses for a voice range of {@code range} blocks, as a share of it. */
    public double referenceFor(double range) {
        return zone.shareOf(range);
    }

    /** Sets the curve and walls for a server whose voice range is {@code range} blocks. */
    public void apply(DistanceConfig config, double range) {
        config.setModel(model);
        config.setAttenuationFactor(rolloff);
        config.setMinVolumeFraction(minVolume);
        config.setOpenalReferenceRatio(referenceFor(range));
        config.setWhisperMultiplier(whisper);
        config.setOcclusionEnabled(occlusion);
        config.setOcclusionStrength(strength);
    }

    /**
     * True when the config currently sounds like this preset for a voice range of {@code range}
     * blocks (wall strength only matters when walls are on).
     */
    public boolean matches(DistanceConfig config, double range) {
        if (config.getModel() != model
                || !near(config.getAttenuationFactor(), rolloff)
                || !near(config.getMinVolumeFraction(), minVolume)
                || !near(config.getOpenalReferenceRatio(), referenceFor(range))
                || !near(config.getWhisperMultiplier(), whisper)
                || config.isOcclusionEnabled() != occlusion) {
            return false;
        }
        return !occlusion || near(config.getOcclusionStrength(), strength);
    }

    public static Preset find(DistanceConfig config, double range) {
        for (Preset p : values()) {
            if (p.matches(config, range)) {
                return p;
            }
        }
        return null;
    }

    /** The preset with this id, or {@code null}. */
    public static Preset byId(String id) {
        for (Preset p : values()) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < EPSILON;
    }
}
