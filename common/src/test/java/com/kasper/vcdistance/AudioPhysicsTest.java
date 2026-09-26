package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

public class AudioPhysicsTest {

    private static final double EPSILON = 1e-4;

    @ParameterizedTest
    @EnumSource(AttenuationModel.class)
    @DisplayName("Gain should be exactly 1.0 at distance 0 for all models")
    void testGainAtZeroDistance(AttenuationModel model) {
        double gain = AudioPhysics.calculateGain(0.0, model, 1.0, 0.0, 0.5);
        assertEquals(1.0, gain, EPSILON, "Volume at 0 distance must be 1.0 for " + model);
    }

    @ParameterizedTest
    @EnumSource(AttenuationModel.class)
    @DisplayName("Gain should be 1.0 anywhere within the reference distance zone")
    void testGainWithinReferenceDistance(AttenuationModel model) {
        double refRatio = 0.6;
        for (double d = 0.0; d <= refRatio; d += 0.1) {
            double gain = AudioPhysics.calculateGain(d, model, 1.0, 0.0, refRatio);
            assertEquals(1.0, gain, EPSILON, "Volume inside reference zone (" + d + " <= " + refRatio + ") must be 1.0 for " + model);
        }
    }

    @Test
    @DisplayName("Linear model follows exact linear falloff slope")
    void testLinearFalloff() {
        double refRatio = 0.5;
        double rolloff = 1.0;
        double minVol = 0.0;

        // At halfway between refRatio (0.5) and max (1.0), dist is 0.75 -> excess = 0.25, remaining = 0.5 -> 50% falloff
        double gain = AudioPhysics.calculateGain(0.75, AttenuationModel.LINEAR, rolloff, minVol, refRatio);
        assertEquals(0.5, gain, EPSILON, "Linear gain at 75% distance should be 0.5");

        // At max distance (1.0), linear gain should reach 0.0
        double gainMax = AudioPhysics.calculateGain(1.0, AttenuationModel.LINEAR, rolloff, minVol, refRatio);
        assertEquals(0.0, gainMax, EPSILON, "Linear gain at 100% distance should be 0.0");
    }

    @Test
    @DisplayName("1/r follows zone / distance, then fades to silence over the last quarter")
    void testRealisticInverseFalloff() {
        double refRatio = 0.5;

        // Before the fade (x < 0.75): gain = ref / (ref + rolloff * (d - ref)); at d = 0.75: 0.5 / 0.75
        assertEquals(2.0 / 3.0, AudioPhysics.calculateGain(0.75, AttenuationModel.REALISTIC_INVERSE, 1.0, 0.0, refRatio), EPSILON);
        // Reaches silence at the edge instead of stopping at 50%
        assertEquals(0.0, AudioPhysics.calculateGain(1.0, AttenuationModel.REALISTIC_INVERSE, 1.0, 0.0, refRatio), EPSILON);

        assertStrictlyDecreasing(AttenuationModel.REALISTIC_INVERSE, refRatio);
    }

    @Test
    @DisplayName("Exponential is a true exponential: steep after the zone, silent at the edge")
    void testExponentialFalloff() {
        double refRatio = 0.5;
        AttenuationModel model = AttenuationModel.EXPONENTIAL;

        assertEquals(0.0, AudioPhysics.calculateGain(1.0, model, 1.0, 0.0, refRatio), EPSILON);

        // A quarter of the way through the fade it is far below the other curves (not 1/r in disguise)
        double exp = AudioPhysics.calculateGain(0.625, model, 1.0, 0.0, refRatio);
        double inverse = AudioPhysics.calculateGain(0.625, AttenuationModel.REALISTIC_INVERSE, 1.0, 0.0, refRatio);
        double linear = AudioPhysics.calculateGain(0.625, AttenuationModel.LINEAR, 1.0, 0.0, refRatio);
        assertTrue(exp < 0.35, "exponential at a quarter of the fade: " + exp);
        assertTrue(exp < inverse && exp < linear, "exponential must be the steepest curve");

        // e^(-4.6 x), normalised to 0 at the edge
        double x = 0.25;
        double edge = Math.exp(-AudioPhysics.EXPONENTIAL_STEEPNESS);
        assertEquals((Math.exp(-AudioPhysics.EXPONENTIAL_STEEPNESS * x) - edge) / (1.0 - edge), exp, EPSILON);

        assertStrictlyDecreasing(model, refRatio);
    }

    @ParameterizedTest
    @EnumSource(AttenuationModel.class)
    @DisplayName("Every curve starts at full volume at the zone edge and ends silent at the range edge")
    void testCurveEnds(AttenuationModel model) {
        for (double refRatio : new double[]{0.05, 0.3, 0.5, 0.9}) {
            assertEquals(1.0, AudioPhysics.calculateGain(refRatio, model, 1.0, 0.0, refRatio), EPSILON, model + " at the zone edge");
            assertEquals(1.0, AudioPhysics.calculateGain(refRatio + 1e-9, model, 1.0, 0.0, refRatio), 1e-4, model + " just past the zone");
            assertEquals(0.0, AudioPhysics.calculateGain(1.0, model, 1.0, 0.0, refRatio), EPSILON, model + " at the range edge");
        }
    }

    @ParameterizedTest
    @EnumSource(AttenuationModel.class)
    @DisplayName("Whispers (falloff above 100%) fade faster and are silent before the edge")
    void testWhisperFalloff(AttenuationModel model) {
        double normal = AudioPhysics.calculateGain(0.7, model, 1.0, 0.0, 0.5);
        double whisper = AudioPhysics.calculateGain(0.7, model, 2.0, 0.0, 0.5);
        assertTrue(whisper < normal, model + ": whisper " + whisper + " vs " + normal);
    }

    private static void assertStrictlyDecreasing(AttenuationModel model, double refRatio) {
        double prev = 1.0;
        for (double d = refRatio + 0.05; d <= 1.0 + 1e-9; d += 0.05) {
            double current = AudioPhysics.calculateGain(d, model, 1.0, 0.0, refRatio);
            assertTrue(current < prev, model + ": gain at " + d + " (" + current + ") should be less than " + prev);
            prev = current;
        }
    }

    @ParameterizedTest
    @EnumSource(AttenuationModel.class)
    @DisplayName("Volume floor (minVol) clamps lower bound across all models")
    void testMinVolumeFloor(AttenuationModel model) {
        double minVol = 0.35;
        // At max distance (1.0) with high rolloff (1.0), calculated gain is below 0.35
        double gain = AudioPhysics.calculateGain(1.0, model, 1.0, minVol, 0.2);
        assertTrue(gain >= minVol - EPSILON, "Gain (" + gain + ") must not drop below minVol (" + minVol + ") for " + model);
    }

    @ParameterizedTest
    @EnumSource(AttenuationModel.class)
    @DisplayName("Zero rolloff keeps volume constant at 1.0")
    void testZeroRolloffConstantVolume(AttenuationModel model) {
        double gain = AudioPhysics.calculateGain(1.0, model, 0.0, 0.0, 0.5);
        assertEquals(1.0, gain, EPSILON, "Zero rolloff must maintain 1.0 gain at maximum distance for " + model);
    }

    @Test
    @DisplayName("Every preset stays within the configurable ranges and is recognised after applying")
    void testPresets() {
        DistanceConfig config = new DistanceConfig(java.nio.file.Path.of("unused.properties"));
        for (Preset preset : Preset.values()) {
            for (double range : new double[]{8, 16, 48, 128, 500}) {
                preset.apply(config, range);
                assertEquals(preset, Preset.find(config, range), "Preset should be detected after applying: " + preset + " at " + range);
                assertTrue(config.getOpenalReferenceRatio() >= DistanceConfig.REFERENCE_MIN && config.getOpenalReferenceRatio() <= DistanceConfig.REFERENCE_MAX);
            }
            assertTrue(config.getAttenuationFactor() >= DistanceConfig.ROLLOFF_MIN && config.getAttenuationFactor() <= DistanceConfig.ROLLOFF_MAX);
            assertTrue(config.getMinVolumeFraction() >= DistanceConfig.MIN_VOLUME_MIN && config.getMinVolumeFraction() <= DistanceConfig.MIN_VOLUME_MAX);
            assertTrue(config.getOpenalReferenceRatio() >= DistanceConfig.REFERENCE_MIN && config.getOpenalReferenceRatio() <= DistanceConfig.REFERENCE_MAX);
            assertTrue(config.getWhisperMultiplier() >= DistanceConfig.WHISPER_MIN && config.getWhisperMultiplier() <= DistanceConfig.WHISPER_MAX);
        }
        Preset.VANILLA.apply(config, 48);
        assertEquals(AttenuationModel.LINEAR, config.getModel());
        assertEquals(1.0, config.getAttenuationFactor(), EPSILON);
        assertEquals(0.5, config.getOpenalReferenceRatio(), EPSILON);
        assertFalse(config.isOcclusionEnabled(), "Vanilla preset must sound exactly like Simple Voice Chat");
        Preset.VANILLA.apply(config, 128);
        assertEquals(0.5, config.getOpenalReferenceRatio(), EPSILON, "Simple Voice Chat's zone is always half the range");
    }

    @Test
    @DisplayName("Realism and stealth keep their full-volume zone in blocks, within limits of the range")
    void testPresetZoneInBlocks() {
        // Realism: 12 blocks, between 5% and 40% of the range
        assertEquals(12.0, Preset.REALISTIC.referenceFor(48) * 48, EPSILON);
        assertEquals(12.0, Preset.REALISTIC.referenceFor(32) * 32, EPSILON);
        assertEquals(0.40, Preset.REALISTIC.referenceFor(16), EPSILON);
        assertEquals(0.05, Preset.REALISTIC.referenceFor(500), EPSILON);
        // Stealth: 7 blocks, at most 30% of the range
        assertEquals(7.0, Preset.ATMOSPHERIC.referenceFor(48) * 48, EPSILON);
        assertEquals(0.30, Preset.ATMOSPHERIC.referenceFor(16), EPSILON);
        // Loudness at a given distance in blocks does not depend on the range (1/r in blocks)
        double at12of48 = AudioPhysics.calculateGain(12.0 / 48, AttenuationModel.REALISTIC_INVERSE, 0.7, 0.0, Preset.REALISTIC.referenceFor(48));
        double at12of96 = AudioPhysics.calculateGain(12.0 / 96, AttenuationModel.REALISTIC_INVERSE, 0.7, 0.0, Preset.REALISTIC.referenceFor(96));
        assertEquals(at12of48, at12of96, 1e-9);
        // Fitted to one range, the preset no longer matches another
        DistanceConfig config = new DistanceConfig(java.nio.file.Path.of("unused.properties"));
        Preset.REALISTIC.apply(config, 48);
        assertTrue(Preset.REALISTIC.matches(config, 48));
        assertFalse(Preset.REALISTIC.matches(config, 96));
        assertTrue(Preset.REALISTIC.dependsOnRange());
        assertFalse(Preset.VANILLA.dependsOnRange());
    }
}
