package com.kasper.vcdistance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    private static final int CONFIG_VERSION = 2;
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
        return c;
    }

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
    public synchronized void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    public synchronized void load() {
        loaded = true;
        Path file = getPath();
        if (!Files.exists(file)) {
            save();
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            LOGGER.error("Failed to read {}, using defaults: {}", file, e.getMessage());
            return;
        }

        model = AttenuationModel.fromId(props.getProperty("distance_model"), DEFAULT_MODEL);
        attenuationFactor = clamp(parseDouble(props, "attenuation_factor", DEFAULT_ATTENUATION_FACTOR), ROLLOFF_MIN, ROLLOFF_MAX);
        minVolumeFraction = clamp(parseDouble(props, "min_volume_fraction", DEFAULT_MIN_VOLUME_FRACTION), MIN_VOLUME_MIN, MIN_VOLUME_MAX);
        openalReferenceRatio = clamp(parseDouble(props, "openal_reference_ratio", DEFAULT_OPENAL_REFERENCE_RATIO), REFERENCE_MIN, REFERENCE_MAX);
        whisperMultiplier = clamp(parseDouble(props, "whisper_multiplier", DEFAULT_WHISPER_MULTIPLIER), WHISPER_MIN, WHISPER_MAX);
        occlusionEnabled = parseBoolean(props, "occlusion_enabled", DEFAULT_OCCLUSION_ENABLED);
        occlusionStrength = clamp(parseDouble(props, "occlusion_strength", DEFAULT_OCCLUSION_STRENGTH), STRENGTH_MIN, STRENGTH_MAX);
        synchronized (materialWeights) {
            for (AcousticMaterial m : AcousticMaterial.values()) {
                materialWeights[m.ordinal()] = clamp(parseDouble(props, "material." + m.getId(), m.getDefaultWeight()), 0.0, AcousticMaterial.MAX_WEIGHT);
            }
        }
        changed();

        LOGGER.info("Configuration loaded: model={}, rolloff={}, floor={}, reference={}, whisper={}, walls={} ({})",
                model.getId(), attenuationFactor, minVolumeFraction, openalReferenceRatio, whisperMultiplier,
                occlusionEnabled, occlusionStrength);

        int version = (int) parseDouble(props, "config_version", 1);
        if (version < CONFIG_VERSION) {
            save();
        }
    }

    public synchronized void save() {
        Path file = getPath();
        Properties props = new Properties();
        props.setProperty("config_version", String.valueOf(CONFIG_VERSION));
        props.setProperty("distance_model", model.getId());
        props.setProperty("attenuation_factor", format(attenuationFactor));
        props.setProperty("min_volume_fraction", format(minVolumeFraction));
        props.setProperty("openal_reference_ratio", format(openalReferenceRatio));
        props.setProperty("whisper_multiplier", format(whisperMultiplier));
        props.setProperty("occlusion_enabled", String.valueOf(occlusionEnabled));
        props.setProperty("occlusion_strength", format(occlusionStrength));
        for (AcousticMaterial m : AcousticMaterial.values()) {
            props.setProperty("material." + m.getId(), format(getMaterialWeight(m)));
        }

        try {
            Path dir = file.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, FILE_NAME, ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                props.store(out, "VoiceChat Audio Distance Addon\n"
                        + "distance_model: linear | realistic_inverse | exponential\n"
                        + "attenuation_factor: falloff strength (0.0 - 1.0)\n"
                        + "min_volume_fraction: volume floor at the edge of hearing range (0.0 - 0.5)\n"
                        + "openal_reference_ratio: share of the distance heard at full volume (0.05 - 1.0)\n"
                        + "whisper_multiplier: falloff multiplier while whispering (0.5 - 2.0)\n"
                        + "occlusion_enabled / occlusion_strength: wall muffling (0.0 - 1.0)\n"
                        + "material.*: acoustic thickness of one block, stone = 1.0 (0.0 - 3.0)");
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save {}: {}", file, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void changed() {
        revision++;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static double parseDouble(Properties props, String key, double fallback) {
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

    private static boolean parseBoolean(Properties props, String key, boolean fallback) {
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
