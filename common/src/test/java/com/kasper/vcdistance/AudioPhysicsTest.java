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
    @DisplayName("Realistic Inverse model follows OpenAL 1/r acoustic propagation")
    void testRealisticInverseFalloff() {
        double refRatio = 0.5;
        double rolloff = 1.0;
        double minVol = 0.0;

        // At max distance (1.0): gain = refRatio / (refRatio + rolloff * (1.0 - refRatio)) = 0.5 / (0.5 + 0.5) = 0.5
        double gain = AudioPhysics.calculateGain(1.0, AttenuationModel.REALISTIC_INVERSE, rolloff, minVol, refRatio);
        assertEquals(0.5, gain, EPSILON, "Inverse gain at max distance with ref=0.5, rolloff=1.0 should be 0.5");

        // Gain must decrease monotonically beyond reference distance
        double prev = 1.0;
        for (double d = refRatio + 0.05; d <= 1.0; d += 0.05) {
            double current = AudioPhysics.calculateGain(d, AttenuationModel.REALISTIC_INVERSE, rolloff, minVol, refRatio);
            assertTrue(current < prev, "Gain at " + d + " (" + current + ") should be less than " + prev);
            prev = current;
        }
    }

    @Test
    @DisplayName("Exponential model matches standard OpenAL formula without artificial distortion")
    void testExponentialFalloff() {
        double refRatio = 0.5;
        double rolloff = 1.0;
        double minVol = 0.0;

        // OpenAL formula: (dist / ref)^(-rolloff)
        // At dist = 1.0: (1.0 / 0.5)^(-1.0) = 2.0^(-1.0) = 0.5
        double gain = AudioPhysics.calculateGain(1.0, AttenuationModel.EXPONENTIAL, rolloff, minVol, refRatio);
        assertEquals(0.5, gain, EPSILON, "Exponential gain at 1.0 with ref=0.5, rolloff=1.0 should be exactly 0.5");
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
    @DisplayName("Clamped OpenAL constants are properly defined in AttenuationModel")
    void testAttenuationModelConstants() {
        // Clamped constants according to OpenAL 1.1 specification
        assertEquals(0xD004, AttenuationModel.LINEAR.getOpenAlConstant(), "LINEAR must use AL_LINEAR_DISTANCE_CLAMPED");
        assertEquals(0xD002, AttenuationModel.REALISTIC_INVERSE.getOpenAlConstant(), "REALISTIC_INVERSE must use AL_INVERSE_DISTANCE_CLAMPED");
        assertEquals(0xD006, AttenuationModel.EXPONENTIAL.getOpenAlConstant(), "EXPONENTIAL must use AL_EXPONENT_DISTANCE_CLAMPED");
    }

    @Test
    @DisplayName("Presets configure valid parameters within bounds")
    void testPresets() {
        DistanceConfig config = new DistanceConfig();

        config.applyDefault();
        assertEquals(AttenuationModel.LINEAR, config.model);
        assertEquals(1.0, config.attenuationFactor, EPSILON);
        assertEquals(0.0, config.minVolumeFraction, EPSILON);
        assertEquals(0.5, config.openalReferenceRatio, EPSILON);
        assertEquals(1.0, config.whisperMultiplier, EPSILON);

        config.applyRealistic();
        assertEquals(AttenuationModel.REALISTIC_INVERSE, config.model);
        assertTrue(config.attenuationFactor > 0.0 && config.attenuationFactor <= 1.0);
        assertTrue(config.openalReferenceRatio >= 0.1 && config.openalReferenceRatio <= 1.0);
        assertTrue(config.whisperMultiplier >= 0.5 && config.whisperMultiplier <= 2.0);

        config.applyHighAudibility();
        assertEquals(AttenuationModel.LINEAR, config.model);
        assertTrue(config.minVolumeFraction > 0.0);

        config.applyAtmospheric();
        assertEquals(AttenuationModel.EXPONENTIAL, config.model);
        assertTrue(config.whisperMultiplier > 1.0);
    }
}
