package com.kasper.vcdistance;

/**
 * Acoustic material classes used by the wall occlusion tracer.
 * <p>
 * The weight is the "acoustic thickness" a single block of the material adds to a ray.
 * One block of stone is the unit (1.0). Weights are user-tunable in the config.
 */
public enum AcousticMaterial {

    STONE("stone", 1.00, "stone, bricks, concrete, ores, metal and every dense block not listed below",
            "камень, кирпич, бетон, руда, металл и все плотные блоки, которых нет ниже"),
    WOOD("wood", 0.70, "logs, planks and wooden blocks", "брёвна, доски и деревянные блоки"),
    WOOL("wool", 1.40, "wool and carpets, the best sound insulation", "шерсть и ковры, лучшая звукоизоляция"),
    GLASS("glass", 0.40, "glass blocks and panes", "стеклянные блоки и панели"),
    DOOR("door", 0.60, "doors and trapdoors", "двери и люки"),
    LEAVES("leaves", 0.15, "leaves, let most of the sound through", "листва, пропускает почти весь звук"),
    THIN("thin", 0.20, "fences, gates and bars, mostly open", "заборы, калитки и решётки, почти открытые"),
    LIQUID("liquid", 0.35, "each block of water or lava", "каждый блок воды или лавы");

    public static final double MAX_WEIGHT = 3.0;

    private final String id;
    private final double defaultWeight;
    private final String english;
    private final String russian;

    AcousticMaterial(String id, double defaultWeight, String english, String russian) {
        this.id = id;
        this.defaultWeight = defaultWeight;
        this.english = english;
        this.russian = russian;
    }

    public String getId() {
        return id;
    }

    public double getDefaultWeight() {
        return defaultWeight;
    }

    /** Comment lines for settings files: English, then Russian, with the default weight. */
    String[] getDescription() {
        String weight = ConfigWriter.number(defaultWeight);
        return new String[]{
                id + ": " + english + ". Default " + weight + ".",
                id + ": " + russian + ". По умолчанию " + weight + "."
        };
    }

    public String getTranslationKey() {
        return "gui.vc-audio-distance.material." + id;
    }

    public String getTooltipKey() {
        return "gui.vc-audio-distance.material." + id + ".tooltip";
    }
}
