package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class DistanceConfigTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("First load writes a config file with defaults")
    void firstLoadCreatesFile() {
        Path file = dir.resolve("sub/vc.properties");
        DistanceConfig config = new DistanceConfig(file);
        config.load();
        assertTrue(Files.exists(file));
        assertEquals(DistanceConfig.DEFAULT_MODEL, config.getModel());
        assertTrue(config.isOcclusionEnabled());
    }

    @Test
    @DisplayName("Save and load round-trips every value")
    void roundTrip() {
        Path file = dir.resolve("vc.properties");
        DistanceConfig a = new DistanceConfig(file);
        a.setModel(AttenuationModel.EXPONENTIAL);
        a.setAttenuationFactor(0.42);
        a.setMinVolumeFraction(0.12);
        a.setOpenalReferenceRatio(0.33);
        a.setWhisperMultiplier(1.7);
        a.setOcclusionEnabled(false);
        a.setOcclusionStrength(0.81);
        a.setMaterialWeight(AcousticMaterial.GLASS, 1.25);
        a.save();

        DistanceConfig b = new DistanceConfig(file);
        b.load();
        assertEquals(AttenuationModel.EXPONENTIAL, b.getModel());
        assertEquals(0.42, b.getAttenuationFactor(), 1e-4);
        assertEquals(0.12, b.getMinVolumeFraction(), 1e-4);
        assertEquals(0.33, b.getOpenalReferenceRatio(), 1e-4);
        assertEquals(1.7, b.getWhisperMultiplier(), 1e-4);
        assertFalse(b.isOcclusionEnabled());
        assertEquals(0.81, b.getOcclusionStrength(), 1e-4);
        assertEquals(1.25, b.getMaterialWeight(AcousticMaterial.GLASS), 1e-4);
        assertEquals(AcousticMaterial.STONE.getDefaultWeight(), b.getMaterialWeight(AcousticMaterial.STONE), 1e-4);
    }

    @Test
    @DisplayName("Broken and out-of-range values fall back or are clamped")
    void brokenValues() throws IOException {
        Path file = dir.resolve("vc.properties");
        Files.writeString(file, String.join("\n",
                "distance_model=nonsense",
                "attenuation_factor=abc",
                "min_volume_fraction=0.9",
                "openal_reference_ratio=-3",
                "whisper_multiplier=NaN",
                "occlusion_enabled=maybe",
                "occlusion_strength=7",
                "material.wool=99"));
        DistanceConfig c = new DistanceConfig(file);
        c.load();
        assertEquals(DistanceConfig.DEFAULT_MODEL, c.getModel());
        assertEquals(DistanceConfig.DEFAULT_ATTENUATION_FACTOR, c.getAttenuationFactor(), 1e-9);
        assertEquals(DistanceConfig.MIN_VOLUME_MAX, c.getMinVolumeFraction(), 1e-9);
        assertEquals(DistanceConfig.REFERENCE_MIN, c.getOpenalReferenceRatio(), 1e-9);
        assertEquals(DistanceConfig.DEFAULT_WHISPER_MULTIPLIER, c.getWhisperMultiplier(), 1e-9);
        assertEquals(DistanceConfig.DEFAULT_OCCLUSION_ENABLED, c.isOcclusionEnabled());
        assertEquals(DistanceConfig.STRENGTH_MAX, c.getOcclusionStrength(), 1e-9);
        assertEquals(AcousticMaterial.MAX_WEIGHT, c.getMaterialWeight(AcousticMaterial.WOOL), 1e-9);
    }

    @Test
    @DisplayName("A 1.1.x config without version is upgraded in place")
    void migratesOldFile() throws IOException {
        Path file = dir.resolve("vc.properties");
        Files.writeString(file, "distance_model=realistic_inverse\nattenuation_factor=0.7\n");
        DistanceConfig c = new DistanceConfig(file);
        c.load();
        assertEquals(AttenuationModel.REALISTIC_INVERSE, c.getModel());
        String text = Files.readString(file);
        assertTrue(text.contains("config_version=3"));
        assertTrue(text.contains("material.stone"));
    }

    @Test
    @DisplayName("The file explains every key in English and Russian, in a fixed order")
    void fileIsReadable() throws IOException {
        Path file = dir.resolve("vc.properties");
        DistanceConfig c = new DistanceConfig(file);
        c.setMaterialWeight(AcousticMaterial.WOOL, 2.5);
        c.save();
        String text = Files.readString(file);
        String[] keys = {"config_version=", "distance_model=", "attenuation_factor=", "openal_reference_ratio=",
                "min_volume_fraction=", "whisper_multiplier=", "occlusion_enabled=", "occlusion_strength=",
                "material.stone=", "material.liquid="};
        int last = -1;
        for (String key : keys) {
            int at = text.indexOf("\n" + key);
            assertTrue(at > last, key + " is missing or out of order");
            last = at;
        }
        // Every key has a comment right above it, and numbers are short
        String[] lines = text.split("\n");
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isEmpty() && !lines[i].startsWith("#")) {
                assertTrue(lines[i - 1].startsWith("#"), "no comment above " + lines[i]);
            }
        }
        assertTrue(text.contains("Кривая громкости"));
        assertTrue(text.contains("material.wool=2.5\n"));
        assertTrue(text.contains("attenuation_factor=1.0\n"));
    }

    @Test
    @DisplayName("Copy/restore gives a working cancel snapshot")
    void copyRestore() {
        DistanceConfig c = new DistanceConfig(dir.resolve("vc.properties"));
        DistanceConfig snapshot = c.copy();
        int rev = c.getRevision();
        c.setAttenuationFactor(0.1);
        c.setMaterialWeight(AcousticMaterial.WOOD, 2.0);
        assertNotEquals(rev, c.getRevision());
        c.copyFrom(snapshot);
        assertEquals(DistanceConfig.DEFAULT_ATTENUATION_FACTOR, c.getAttenuationFactor(), 1e-9);
        assertEquals(AcousticMaterial.WOOD.getDefaultWeight(), c.getMaterialWeight(AcousticMaterial.WOOD), 1e-9);
    }

    @Test
    @DisplayName("Setters clamp to the documented ranges")
    void settersClamp() {
        DistanceConfig c = new DistanceConfig(dir.resolve("vc.properties"));
        c.setAttenuationFactor(5);
        c.setWhisperMultiplier(0);
        c.setOcclusionStrength(-1);
        assertEquals(DistanceConfig.ROLLOFF_MAX, c.getAttenuationFactor());
        assertEquals(DistanceConfig.WHISPER_MIN, c.getWhisperMultiplier());
        assertEquals(DistanceConfig.STRENGTH_MIN, c.getOcclusionStrength());
    }
}
