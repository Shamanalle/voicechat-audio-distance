package com.kasper.vcdistance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Configuration manager for VoiceChat Audio Distance Addon.
 * Stored in config/vc-audio-distance.properties
 */
public class DistanceConfig {

    public static final Logger LOGGER = LoggerFactory.getLogger("VC-AudioDistance");
    private static final Path CONFIG_PATH = resolveConfigPath();

    private static Path resolveConfigPath() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve("vc-audio-distance.properties");
        } catch (Throwable ignored) {
            return Path.of("config", "vc-audio-distance.properties");
        }
    }

    // -------------------------------------------------------------------------
    // Vanilla Simple Voice Chat Defaults
    // -------------------------------------------------------------------------
    public static final AttenuationModel DEFAULT_MODEL               = AttenuationModel.LINEAR;
    public static final double DEFAULT_ATTENUATION_FACTOR            = 1.0;
    public static final double DEFAULT_MIN_VOLUME_FRACTION           = 0.0;
    public static final double DEFAULT_OPENAL_REFERENCE_RATIO        = 0.5;
    public static final double DEFAULT_WHISPER_MULTIPLIER            = 1.0;
    public static final boolean DEFAULT_OCCLUSION_ENABLED            = true;
    public static final double DEFAULT_OCCLUSION_STRENGTH            = 0.60;

    // -------------------------------------------------------------------------
    // Configuration Fields (volatile for thread safety between GUI & OpenAL)
    // -------------------------------------------------------------------------

    /** OpenAL distance attenuation model */
    public volatile AttenuationModel model = DEFAULT_MODEL;

    /**
     * Rolloff factor (0.0 - 1.0):
     * Controls decay steepness. 0 = constant volume, 1 = normal decay.
     */
    public volatile double attenuationFactor = DEFAULT_ATTENUATION_FACTOR;

    /**
     * Hardware volume floor via AL_MIN_GAIN (0.0 - 1.0):
     * Ensures distant voice never drops below this volume level.
     */
    public volatile double minVolumeFraction = DEFAULT_MIN_VOLUME_FRACTION;

    /**
     * Point where volume decay begins (0.1 - 1.0 fraction of max distance).
     * 0.5 = vanilla SVC default (decay starts halfway).
     */
    public volatile double openalReferenceRatio = DEFAULT_OPENAL_REFERENCE_RATIO;

    /**
     * Decay multiplier when the speaker is whispering (0.5 - 2.0).
     * 1.0 = normal whisper decay. Higher = whisper drops off faster.
     */
    public volatile double whisperMultiplier = DEFAULT_WHISPER_MULTIPLIER;

    /**
     * Physical sound occlusion through walls/obstacles.
     * Muffles sound when solid blocks are between speaker and listener.
     */
    public volatile boolean occlusionEnabled = DEFAULT_OCCLUSION_ENABLED;

    /**
     * Muffling strength through solid obstacles (0.0 - 1.0).
     * 0.0 = minimal muffling, 1.0 = deep acoustic absorption.
     */
    public volatile double occlusionStrength = DEFAULT_OCCLUSION_STRENGTH;

    public void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            props.load(in);
            model                = AttenuationModel.fromId(props.getProperty("distance_model"), DEFAULT_MODEL);
            attenuationFactor    = clamp(parseDouble(props, "attenuation_factor", attenuationFactor), 0.0, 1.0);
            minVolumeFraction    = clamp(parseDouble(props, "min_volume_fraction", minVolumeFraction), 0.0, 1.0);
            openalReferenceRatio = clamp(parseDouble(props, "openal_reference_ratio", openalReferenceRatio), 0.1, 1.0);
            whisperMultiplier    = clamp(parseDouble(props, "whisper_multiplier", whisperMultiplier), 0.5, 2.0);
            occlusionEnabled     = parseBoolean(props, "occlusion_enabled", occlusionEnabled);
            occlusionStrength    = clamp(parseDouble(props, "occlusion_strength", occlusionStrength), 0.0, 1.0);

            LOGGER.info("Configuration loaded: model={}, attenuation={}, minVolume={}, refRatio={}, whisperMult={}, occlusion={}, occlusionStrength={}",
                    model.getId(), attenuationFactor, minVolumeFraction, openalReferenceRatio, whisperMultiplier, occlusionEnabled, occlusionStrength);
        } catch (IOException e) {
            LOGGER.error("Failed to load configuration file: {}", e.getMessage(), e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Properties props = new Properties();
            props.setProperty("distance_model", model.getId());
            props.setProperty("attenuation_factor", String.format(Locale.ROOT, "%.4f", attenuationFactor));
            props.setProperty("min_volume_fraction", String.format(Locale.ROOT, "%.4f", minVolumeFraction));
            props.setProperty("openal_reference_ratio", String.format(Locale.ROOT, "%.4f", openalReferenceRatio));
            props.setProperty("whisper_multiplier", String.format(Locale.ROOT, "%.4f", whisperMultiplier));
            props.setProperty("occlusion_enabled", String.valueOf(occlusionEnabled));
            props.setProperty("occlusion_strength", String.format(Locale.ROOT, "%.4f", occlusionStrength));

            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                props.store(out, "VoiceChat Audio Distance Addon Configuration\n"
                        + "distance_model: linear, realistic_inverse, or exponential\n"
                        + "attenuation_factor: OpenAL rolloff (0.0=none, 1.0=vanilla)\n"
                        + "min_volume_fraction: Hardware volume floor (0.0-1.0)\n"
                        + "openal_reference_ratio: Distance fraction where drop begins (0.1-1.0, default 0.5)\n"
                        + "whisper_multiplier: Decay rate multiplier for whispers (0.5-2.0, default 1.0)\n"
                        + "occlusion_enabled: Sound muffling through walls and solid blocks (true/false)\n"
                        + "occlusion_strength: Wall muffling intensity (0.0-1.0, default 0.60)");
            }
            LOGGER.info("Configuration saved successfully.");
        } catch (IOException e) {
            LOGGER.error("Failed to save configuration file: {}", e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Presets
    // -------------------------------------------------------------------------

    /** Vanilla Simple Voice Chat behavior */
    public void applyDefault() {
        model                = AttenuationModel.LINEAR;
        attenuationFactor    = DEFAULT_ATTENUATION_FACTOR;
        minVolumeFraction    = DEFAULT_MIN_VOLUME_FRACTION;
        openalReferenceRatio = DEFAULT_OPENAL_REFERENCE_RATIO;
        whisperMultiplier    = DEFAULT_WHISPER_MULTIPLIER;
        occlusionEnabled     = false;
        occlusionStrength    = DEFAULT_OCCLUSION_STRENGTH;
    }

    /** Realistic acoustic propagation based on inverse distance */
    public void applyRealistic() {
        model                = AttenuationModel.REALISTIC_INVERSE;
        attenuationFactor    = 0.70;
        minVolumeFraction    = 0.05;
        openalReferenceRatio = 0.60;
        whisperMultiplier    = 1.10;
        occlusionEnabled     = true;
        occlusionStrength    = 0.65;
    }

    /** Competitive / High audibility for large servers and events */
    public void applyHighAudibility() {
        model                = AttenuationModel.LINEAR;
        attenuationFactor    = 0.35;
        minVolumeFraction    = 0.25;
        openalReferenceRatio = 0.80;
        whisperMultiplier    = 0.90;
        occlusionEnabled     = false;
        occlusionStrength    = 0.30;
    }

    /** Atmospheric / Stealth decay */
    public void applyAtmospheric() {
        model                = AttenuationModel.EXPONENTIAL;
        attenuationFactor    = 1.00;
        minVolumeFraction    = 0.00;
        openalReferenceRatio = 0.35;
        whisperMultiplier    = 1.40;
        occlusionEnabled     = true;
        occlusionStrength    = 0.85;
    }

    private static double parseDouble(Properties props, String key, double defaultValue) {
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean parseBoolean(Properties props, String key, boolean defaultValue) {
        String val = props.getProperty(key);
        if (val == null) return defaultValue;
        return Boolean.parseBoolean(val.trim());
    }

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}
