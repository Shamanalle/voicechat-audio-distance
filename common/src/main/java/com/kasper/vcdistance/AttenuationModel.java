package com.kasper.vcdistance;

/**
 * OpenAL distance attenuation models supported by the addon.
 */
public enum AttenuationModel {

    /**
     * Standard Simple Voice Chat linear distance model.
     * Sound stays at 100% until reference distance, then decreases linearly to 0.
     */
    LINEAR(
            "linear",
            "gui.vc-audio-distance.model.linear",
            "gui.vc-audio-distance.model.linear.tooltip",
            0xD004 // AL_LINEAR_DISTANCE_CLAMPED
    ),

    /**
     * Physically realistic inverse distance model (1/r acoustic propagation).
     * Mimics real-world sound physics where volume drops off smoothly and naturally.
     */
    REALISTIC_INVERSE(
            "realistic_inverse",
            "gui.vc-audio-distance.model.inverse",
            "gui.vc-audio-distance.model.inverse.tooltip",
            0xD002 // AL_INVERSE_DISTANCE_CLAMPED
    ),

    /**
     * Exponential distance decay.
     * Fast initial drop-off, suitable for atmospheric or stealth gameplay.
     */
    EXPONENTIAL(
            "exponential",
            "gui.vc-audio-distance.model.exponential",
            "gui.vc-audio-distance.model.exponential.tooltip",
            0xD006 // AL_EXPONENT_DISTANCE_CLAMPED
    );

    private final String id;
    private final String translationKey;
    private final String tooltipKey;
    private final int openAlConstant;

    AttenuationModel(String id, String translationKey, String tooltipKey, int openAlConstant) {
        this.id = id;
        this.translationKey = translationKey;
        this.tooltipKey = tooltipKey;
        this.openAlConstant = openAlConstant;
    }

    public String getId() {
        return id;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public String getTooltipKey() {
        return tooltipKey;
    }

    public int getOpenAlConstant() {
        return openAlConstant;
    }

    public AttenuationModel next() {
        AttenuationModel[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static AttenuationModel fromId(String id, AttenuationModel fallback) {
        if (id == null) return fallback;
        for (AttenuationModel model : values()) {
            if (model.id.equalsIgnoreCase(id.trim())) {
                return model;
            }
        }
        return fallback;
    }
}
