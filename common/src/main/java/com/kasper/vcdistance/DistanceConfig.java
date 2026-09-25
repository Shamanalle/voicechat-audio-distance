package com.kasper.vcdistance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Configuration of the addon, stored in {@code config/vc-audio-distance.properties}.
 * <p>
 * Values are read by the audio threads on every voice frame and written by the settings screen,
 * so every field is volatile and every setter clamps to the valid range.
 */
public final class DistanceConfig {

    public static final Logger LOGGER = LoggerFactory.getLogger("VC-AudioDistance");

    /** 3: the file is written with a comment for every key; 4: interface section. */
    private static final int CONFIG_VERSION = 4;
    private static final String FILE_NAME = "vc-audio-distance.properties";

    // -------------------------------------------------------------------------
    // Ranges
    // -------------------------------------------------------------------------
    public static final double ROLLOFF_MIN = 0.0;
    public static final double ROLLOFF_MAX = 1.0;
    public static final double MIN_VOLUME_MIN = 0.0;
    public static final double MIN_VOLUME_MAX = 0.5;
    public static final double REFERENCE_MIN = 0.05;
    public static final double REFERENCE_MAX = 1.0;
    public static final double WHISPER_MIN = 0.5;
    public static final double WHISPER_MAX = 2.0;
    public static final double STRENGTH_MIN = 0.0;
    public static final double STRENGTH_MAX = 1.0;

    // -------------------------------------------------------------------------
    // Defaults (first launch and the "Reset" button):
    // Simple Voice Chat's own distance curve plus wall muffling.
    // -------------------------------------------------------------------------
    public static final AttenuationModel DEFAULT_MODEL = AttenuationModel.LINEAR;
    public static final double DEFAULT_ATTENUATION_FACTOR = 1.0;
    public static final double DEFAULT_MIN_VOLUME_FRACTION = 0.0;
    public static final double DEFAULT_OPENAL_REFERENCE_RATIO = 0.5;
    public static final double DEFAULT_WHISPER_MULTIPLIER = 1.0;
    public static final boolean DEFAULT_OCCLUSION_ENABLED = true;
    public static final double DEFAULT_OCCLUSION_STRENGTH = 0.60;

    private volatile AttenuationModel model = DEFAULT_MODEL;
    private volatile double attenuationFactor = DEFAULT_ATTENUATION_FACTOR;
    private volatile double minVolumeFraction = DEFAULT_MIN_VOLUME_FRACTION;
    private volatile double openalReferenceRatio = DEFAULT_OPENAL_REFERENCE_RATIO;
    private volatile double whisperMultiplier = DEFAULT_WHISPER_MULTIPLIER;
    private volatile boolean occlusionEnabled = DEFAULT_OCCLUSION_ENABLED;
    private volatile double occlusionStrength = DEFAULT_OCCLUSION_STRENGTH;
    private final double[] materialWeights = new double[AcousticMaterial.values().length];

    // Interface: only ever read from the player's own file, never part of a server profile
    public static final HudMode DEFAULT_HUD_MODE = HudMode.TALKING;
    public static final HudCorner DEFAULT_HUD_CORNER = HudCorner.TOP_LEFT;
    private volatile HudMode hudMode = DEFAULT_HUD_MODE;
    private volatile HudCorner hudCorner = DEFAULT_HUD_CORNER;
    private volatile boolean welcomeShown;

    private volatile int revision;
    private final Path path;
    private volatile boolean loaded;

    public DistanceConfig() {
        this(null);
    }

    /**
     * @param path file to use, or {@code null} to resolve the loader's config directory lazily
     */
    public DistanceConfig(Path path) {
        this.path = path;
        resetMaterials();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public AttenuationModel getModel() {
        return model;
    }

    public void setModel(AttenuationModel model) {
        this.model = model == null ? DEFAULT_MODEL : model;
        changed();
    }

    /** OpenAL rolloff factor: 0 = no falloff, 1 = full falloff. */
    public double getAttenuationFactor() {
        return attenuationFactor;
    }

    public void setAttenuationFactor(double value) {
        attenuationFactor = clamp(value, ROLLOFF_MIN, ROLLOFF_MAX);
        changed();
    }

    /** Volume floor at the edge of hearing range, relative to the speaker's volume (AL_MIN_GAIN). */
    public double getMinVolumeFraction() {
        return minVolumeFraction;
    }

    public void setMinVolumeFraction(double value) {
        minVolumeFraction = clamp(value, MIN_VOLUME_MIN, MIN_VOLUME_MAX);
        changed();
    }

    /** Fraction of the hearing distance at which the falloff begins (AL_REFERENCE_DISTANCE). */
    public double getOpenalReferenceRatio() {
        return openalReferenceRatio;
    }

    public void setOpenalReferenceRatio(double value) {
        openalReferenceRatio = clamp(value, REFERENCE_MIN, REFERENCE_MAX);
        changed();
    }

    /** Rolloff multiplier applied while the speaker whispers. */
    public double getWhisperMultiplier() {
        return whisperMultiplier;
    }

    public void setWhisperMultiplier(double value) {
        whisperMultiplier = clamp(value, WHISPER_MIN, WHISPER_MAX);
        changed();
    }

    public boolean isOcclusionEnabled() {
        return occlusionEnabled;
    }

    public void setOcclusionEnabled(boolean value) {
        occlusionEnabled = value;
        changed();
    }

    public double getOcclusionStrength() {
        return occlusionStrength;
    }

    public void setOcclusionStrength(double value) {
        occlusionStrength = clamp(value, STRENGTH_MIN, STRENGTH_MAX);
        changed();
    }

    public double getMaterialWeight(AcousticMaterial material) {
        synchronized (materialWeights) {
            return materialWeights[material.ordinal()];
        }
    }

    public void setMaterialWeight(AcousticMaterial material, double weight) {
        synchronized (materialWeights) {
            materialWeights[material.ordinal()] = clamp(weight, 0.0, AcousticMaterial.MAX_WEIGHT);
        }
        changed();
    }

    public void resetMaterials() {
        synchronized (materialWeights) {
            for (AcousticMaterial m : AcousticMaterial.values()) {
                materialWeights[m.ordinal()] = m.getDefaultWeight();
            }
        }
        changed();
    }

    // ---- Interface (not part of a server profile, not touched by copyFrom) ----

    public HudMode getHudMode() {
        return hudMode;
    }

    public void setHudMode(HudMode mode) {
        hudMode = mode == null ? DEFAULT_HUD_MODE : mode;
        changed();
    }

    public HudCorner getHudCorner() {
        return hudCorner;
    }

    public void setHudCorner(HudCorner corner) {
        hudCorner = corner == null ? DEFAULT_HUD_CORNER : corner;
        changed();
    }

    /** {@code true} once the first-join hint was shown. */
    public boolean isWelcomeShown() {
        return welcomeShown;
    }

    public void setWelcomeShown(boolean shown) {
        welcomeShown = shown;
        changed();
    }

    /** Copies the interface settings (HUD); {@link #copyFrom} leaves them alone. */
    public void copyInterfaceFrom(DistanceConfig other) {
        hudMode = other.hudMode;
        hudCorner = other.hudCorner;
        welcomeShown = other.welcomeShown;
        changed();
    }

    /** Incremented on every change; lets caches and UI detect edits cheaply. */
    public int getRevision() {
        return revision;
    }

    // -------------------------------------------------------------------------
    // Bulk operations
    // -------------------------------------------------------------------------

    /** Restores first-launch defaults, including material weights. */
    public void applyDefaults() {
        model = DEFAULT_MODEL;
        attenuationFactor = DEFAULT_ATTENUATION_FACTOR;
        minVolumeFraction = DEFAULT_MIN_VOLUME_FRACTION;
        openalReferenceRatio = DEFAULT_OPENAL_REFERENCE_RATIO;
        whisperMultiplier = DEFAULT_WHISPER_MULTIPLIER;
        occlusionEnabled = DEFAULT_OCCLUSION_ENABLED;
        occlusionStrength = DEFAULT_OCCLUSION_STRENGTH;
        resetMaterials();
    }

    public DistanceConfig copy() {
        DistanceConfig c = new DistanceConfig(path);
        c.copyFrom(this);
        c.copyInterfaceFrom(this);
        return c;
    }

    /** Copies the sound settings (everything a server profile holds), not the interface. */
    public void copyFrom(DistanceConfig other) {
        model = other.model;
        attenuationFactor = other.attenuationFactor;
        minVolumeFraction = other.minVolumeFraction;
        openalReferenceRatio = other.openalReferenceRatio;
        whisperMultiplier = other.whisperMultiplier;
        occlusionEnabled = other.occlusionEnabled;
        occlusionStrength = other.occlusionStrength;
        for (AcousticMaterial m : AcousticMaterial.values()) {
            double w = other.getMaterialWeight(m);
            synchronized (materialWeights) {
                materialWeights[m.ordinal()] = w;
            }
        }
        changed();
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    public Path getPath() {
        return path != null ? path : ModEnvironment.configDir().resolve(FILE_NAME);
    }

    /** Loads the config once; later calls are no-ops. */
    public void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (this) {
            if (!loaded) {
                load();
            }
        }
    }

    public synchronized void load() {
        loaded = true;
        Path file = getPath();
        if (!Files.exists(file)) {
            save();
            return;
        }
        Properties props;
        try {
            props = ConfigWriter.load(file);
        } catch (IOException e) {
            LOGGER.error("Failed to read {}, using defaults: {}", file, e.getMessage());
            return;
        }
        readFrom(props, "");
        hudMode = HudMode.fromId(props.getProperty("hud_mode"), DEFAULT_HUD_MODE);
        hudCorner = HudCorner.fromId(props.getProperty("hud_corner"), DEFAULT_HUD_CORNER);
        welcomeShown = parseBoolean(props, "welcome_shown", false);

        LOGGER.info("Configuration loaded: model={}, rolloff={}, floor={}, reference={}, whisper={}, walls={} ({})",
                model.getId(), attenuationFactor, minVolumeFraction, openalReferenceRatio, whisperMultiplier,
                occlusionEnabled, occlusionStrength);

        int version = (int) parseDouble(props, "config_version", 1);
        if (version < CONFIG_VERSION) {
            save();
        }
    }

    public synchronized void save() {
        ConfigWriter w = new ConfigWriter()
                .title("VoiceChat Audio Distance - client settings",
                        "Easier to change in game: voice chat settings (V) -> \"Voice distance & walls...\".",
                        "The voice and whisper range itself is set by the server (Simple Voice Chat).",
                        "",
                        "VoiceChat Audio Distance - настройки клиента",
                        "Удобнее менять в игре: настройки голосового чата (V) -> «Дальность голоса и стены…».",
                        "Сама дальность голоса и шёпота задаётся на сервере (Simple Voice Chat).")
                .comment("Format version, do not change. / Версия формата, не меняйте.")
                .value("config_version", CONFIG_VERSION);
        w.section("Distance curve", "Кривая громкости");
        writeCurve(w, "");
        w.section("Walls", "Стены");
        writeWalls(w, "");
        writeMaterials(w, "");
        w.section("Interface", "Интерфейс");
        w.comment("Voice HUD on screen: off, talking (while someone nearby or you talk), always. Default talking.",
                        "HUD голоса на экране: off (выкл.), talking (пока кто-то рядом или вы говорите), always (всегда). По умолчанию talking.")
                .value("hud_mode", hudMode.getId())
                .comment("Corner of the voice HUD: top_left, top_right, bottom_left, bottom_right. Default top_left.",
                        "Угол экрана для HUD: top_left, top_right, bottom_left, bottom_right. По умолчанию top_left.")
                .value("hud_corner", hudCorner.getId())
                .comment("The first-join hint was shown. / Подсказка при первом входе уже показана.")
                .value("welcome_shown", welcomeShown);
        w.save(getPath());
    }

    /** The distance curve keys, each with its explanation (shared by the client and server files). */
    void writeCurve(ConfigWriter w, String prefix) {
        w.comment("Shape of the curve: linear (as in Simple Voice Chat), realistic_inverse (natural, 1/r),",
                        "exponential (fades fast).",
                        "Форма кривой: linear (как в Simple Voice Chat), realistic_inverse (естественная, 1/r),",
                        "exponential (быстро затихает).")
                .value(prefix + "distance_model", model.getId())
                .comment("How strongly voices fade with distance, 0 - 1. Lower: voices carry further. Default 1.0.",
                        "Насколько сильно голос затихает с расстоянием, 0 - 1. Меньше - голос слышно дальше. По умолчанию 1.0.")
                .value(prefix + "attenuation_factor", attenuationFactor)
                .comment("Share of the range heard at full volume, 0.05 - 1. 0.5: the first half of the range. Default 0.5.",
                        "Доля дальности, где слышно в полную громкость, 0.05 - 1. 0.5 - первая половина дальности. По умолчанию 0.5.")
                .value(prefix + "openal_reference_ratio", openalReferenceRatio)
                .comment("Volume at the edge of the range, 0 - 0.5. 0.1 = 10%, 0 = silence. Default 0.",
                        "Громкость на краю слышимости, 0 - 0.5. 0.1 = 10%, 0 = тишина. По умолчанию 0.")
                .value(prefix + "min_volume_fraction", minVolumeFraction)
                .comment("How much faster whispers fade, 0.5 - 2. 1 = like normal speech. Default 1.0.",
                        "Во сколько раз быстрее затихает шёпот, 0.5 - 2. 1 = как обычная речь. По умолчанию 1.0.")
                .value(prefix + "whisper_multiplier", whisperMultiplier);
    }

    private void writeWalls(ConfigWriter w, String prefix) {
        w.comment("Muffle voices behind walls: true / false. Default true.",
                        "Глушить голоса за стенами: true / false. По умолчанию true.")
                .value(prefix + "occlusion_enabled", occlusionEnabled)
                .comment("How strongly walls muffle, 0 - 1. Default 0.6.",
                        "Насколько сильно глушат стены, 0 - 1. По умолчанию 0.6.")
                .value(prefix + "occlusion_strength", occlusionStrength);
    }

    /** Per-material weights with their explanations (shared by the client and server files). */
    void writeMaterials(ConfigWriter w, String prefix) {
        w.comment("How much one block of each material muffles, 0 - 3. Stone = 1.0; 2.0 = like two stone blocks.",
                "Насколько глушит один блок каждого материала, 0 - 3. Камень = 1.0; 2.0 = как два блока камня.");
        for (AcousticMaterial m : AcousticMaterial.values()) {
            w.comment(m.getDescription())
                    .value(prefix + "material." + m.getId(), getMaterialWeight(m));
        }
    }

    /** Writes every setting under {@code prefix} (e.g. {@code "profile."}), for the client-server protocol. */
    public void writeTo(Properties props, String prefix) {
        props.setProperty(prefix + "distance_model", model.getId());
        props.setProperty(prefix + "attenuation_factor", format(attenuationFactor));
        props.setProperty(prefix + "min_volume_fraction", format(minVolumeFraction));
        props.setProperty(prefix + "openal_reference_ratio", format(openalReferenceRatio));
        props.setProperty(prefix + "whisper_multiplier", format(whisperMultiplier));
        props.setProperty(prefix + "occlusion_enabled", String.valueOf(occlusionEnabled));
        props.setProperty(prefix + "occlusion_strength", format(occlusionStrength));
        for (AcousticMaterial m : AcousticMaterial.values()) {
            props.setProperty(prefix + "material." + m.getId(), format(getMaterialWeight(m)));
        }
    }

    /** Reads every setting under {@code prefix}; missing or broken values fall back to the defaults. */
    public void readFrom(Properties props, String prefix) {
        model = AttenuationModel.fromId(props.getProperty(prefix + "distance_model"), DEFAULT_MODEL);
        attenuationFactor = clamp(parseDouble(props, prefix + "attenuation_factor", DEFAULT_ATTENUATION_FACTOR), ROLLOFF_MIN, ROLLOFF_MAX);
        minVolumeFraction = clamp(parseDouble(props, prefix + "min_volume_fraction", DEFAULT_MIN_VOLUME_FRACTION), MIN_VOLUME_MIN, MIN_VOLUME_MAX);
        openalReferenceRatio = clamp(parseDouble(props, prefix + "openal_reference_ratio", DEFAULT_OPENAL_REFERENCE_RATIO), REFERENCE_MIN, REFERENCE_MAX);
        whisperMultiplier = clamp(parseDouble(props, prefix + "whisper_multiplier", DEFAULT_WHISPER_MULTIPLIER), WHISPER_MIN, WHISPER_MAX);
        occlusionEnabled = parseBoolean(props, prefix + "occlusion_enabled", DEFAULT_OCCLUSION_ENABLED);
        occlusionStrength = clamp(parseDouble(props, prefix + "occlusion_strength", DEFAULT_OCCLUSION_STRENGTH), STRENGTH_MIN, STRENGTH_MAX);
        synchronized (materialWeights) {
            for (AcousticMaterial m : AcousticMaterial.values()) {
                materialWeights[m.ordinal()] = clamp(parseDouble(props, prefix + "material." + m.getId(), m.getDefaultWeight()), 0.0, AcousticMaterial.MAX_WEIGHT);
            }
        }
        changed();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void changed() {
        revision++;
    }

    static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    static double parseDouble(Properties props, String key, double fallback) {
        String val = props.getProperty(key);
        if (val == null) {
            return fallback;
        }
        try {
            double d = Double.parseDouble(val.trim());
            return Double.isFinite(d) ? d : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static boolean parseBoolean(Properties props, String key, boolean fallback) {
        String val = props.getProperty(key);
        if (val == null) {
            return fallback;
        }
        String v = val.trim();
        if (v.equalsIgnoreCase("true")) {
            return true;
        }
        if (v.equalsIgnoreCase("false")) {
            return false;
        }
        return fallback;
    }

    static double clamp(double val, double min, double max) {
        if (Double.isNaN(val)) {
            return min;
        }
        return Math.max(min, Math.min(max, val));
    }
}
