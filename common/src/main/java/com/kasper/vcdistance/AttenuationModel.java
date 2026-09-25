package com.kasper.vcdistance;

/**
 * Distance curves. The formulas are in {@link AudioPhysics}; the addon applies them itself instead
 * of OpenAL's distance models, so each curve fades to silence at the edge of the range.
 */
public enum AttenuationModel {

    /**
     * Simple Voice Chat's own curve: full volume up to the reference distance, then a straight
     * fade to silence at the edge.
     */
    LINEAR(
            "linear",
            "gui.vc-audio-distance.model.linear",
            "gui.vc-audio-distance.model.linear.tooltip"
    ),

    /**
     * Sound in the open (1/r): loudness drops quickly at first, then slowly, and fades to silence
     * over the last quarter of the range.
     */
    REALISTIC_INVERSE(
            "realistic_inverse",
            "gui.vc-audio-distance.model.inverse",
            "gui.vc-audio-distance.model.inverse.tooltip"
    ),

    /**
     * Exponential decay: most of the loudness is gone within the first third past the
     * full-volume zone, reaching silence at the edge. Suits horror and stealth.
     */
    EXPONENTIAL(
            "exponential",
            "gui.vc-audio-distance.model.exponential",
            "gui.vc-audio-distance.model.exponential.tooltip"
    );

    private final String id;
    private final String translationKey;
    private final String tooltipKey;

    AttenuationModel(String id, String translationKey, String tooltipKey) {
        this.id = id;
        this.translationKey = translationKey;
        this.tooltipKey = tooltipKey;
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
