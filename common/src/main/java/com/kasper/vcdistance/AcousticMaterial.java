package com.kasper.vcdistance;

/**
 * Acoustic material classes used by the wall occlusion tracer.
 * <p>
 * The weight is the "acoustic thickness" a single block of the material adds to a ray.
 * One block of stone is the unit (1.0). Weights are user-tunable in the config. Absorption and
 * damping describe the echo off a surface of the material and are fixed.
 */
public enum AcousticMaterial {

    // The order is the order of the Materials tab
    STONE("stone", 1.00, 0.03, 0.15, "stone, bricks, concrete, terracotta, ores: everything mined with a pickaxe",
            "камень, кирпич, бетон, терракота, руда: всё, что добывается киркой"),
    METAL("metal", 1.30, 0.03, 0.10, "blocks of iron, gold, copper, netherite and other metal, anvils",
            "блоки железа, золота, меди, незерита и другого металла, наковальни"),
    EARTH("earth", 0.90, 0.15, 0.45, "dirt, grass, sand, gravel, clay, mud, snow: everything dug with a shovel",
            "земля, дёрн, песок, гравий, глина, грязь, снег: всё, что копается лопатой"),
    WOOD("wood", 0.70, 0.12, 0.55, "logs, planks and wooden blocks: everything chopped with an axe",
            "брёвна, доски и деревянные блоки: всё, что рубится топором"),
    WOOL("wool", 1.40, 0.70, 0.90, "wool and carpets, the best sound insulation", "шерсть и ковры, лучшая звукоизоляция"),
    SOFT("soft", 1.20, 0.60, 0.85, "hay, sponge, moss, sculk, dried kelp: soft blocks that swallow sound",
            "сено, губка, мох, скалк, сушёная ламинария: мягкие блоки, которые поглощают звук"),
    GLASS("glass", 0.40, 0.04, 0.10, "glass blocks and panes", "стеклянные блоки и панели"),
    ICE("ice", 0.70, 0.03, 0.10, "ice, packed ice and blue ice", "лёд, плотный и синий лёд"),
    DOOR("door", 0.60, 0.12, 0.55, "doors and trapdoors", "двери и люки"),
    LEAVES("leaves", 0.15, 0.90, 0.80, "leaves, let most of the sound through", "листва, пропускает почти весь звук"),
    THIN("thin", 0.20, 0.90, 0.50, "fences, gates and bars, mostly open", "заборы, калитки и решётки, почти открытые"),
    LIQUID("liquid", 0.35, 0.05, 0.30, "each block of water or lava", "каждый блок воды или лавы"),
    OTHER("other", 1.00, 0.05, 0.30, "every other solid block: bedrock, blocks from other mods and anything not listed above",
            "все остальные твёрдые блоки: бедрок, блоки из других модов и всё, чего нет выше");

    public static final double MAX_WEIGHT = 3.0;

    private final String id;
    private final double defaultWeight;
    private final double absorption;
    private final double damping;
    private final String english;
    private final String russian;

    AcousticMaterial(String id, double defaultWeight, double absorption, double damping, String english, String russian) {
        this.id = id;
        this.defaultWeight = defaultWeight;
        this.absorption = absorption;
        this.damping = damping;
        this.english = english;
        this.russian = russian;
    }

    public String getId() {
        return id;
    }

    public double getDefaultWeight() {
        return defaultWeight;
    }

    /**
     * Share of the sound a surface of this material swallows instead of reflecting, 0 - 1: stone
     * and metal echo, wool and leaves hardly at all. Fixed; not the wall weight.
     */
    public double getAbsorption() {
        return absorption;
    }

    /** How much faster the highs of an echo die off this material, 0 - 1: stone rings, wood and wool sound warm. */
    public double getDamping() {
        return damping;
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
