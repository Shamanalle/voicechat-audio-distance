package com.kasper.vcdistance;

/**
 * Acoustic material classes used by the wall occlusion tracer.
 * <p>
 * The weight is the "acoustic thickness" a single block of the material adds to a ray.
 * One block of stone is the unit (1.0). Weights are user-tunable in the config.
 */
public enum AcousticMaterial {

    STONE("stone", 1.00),
    WOOD("wood", 0.70),
    WOOL("wool", 1.40),
    GLASS("glass", 0.40),
    DOOR("door", 0.60),
    LEAVES("leaves", 0.15),
    THIN("thin", 0.20),
    LIQUID("liquid", 0.35);

    public static final double MAX_WEIGHT = 3.0;

    private final String id;
    private final double defaultWeight;

    AcousticMaterial(String id, double defaultWeight) {
        this.id = id;
        this.defaultWeight = defaultWeight;
    }

    public String getId() {
        return id;
    }

    public double getDefaultWeight() {
        return defaultWeight;
    }

    public String getTranslationKey() {
        return "gui.vc-audio-distance.material." + id;
    }

    public String getTooltipKey() {
        return "gui.vc-audio-distance.material." + id + ".tooltip";
    }
}
