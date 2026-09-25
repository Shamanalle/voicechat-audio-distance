package com.kasper.vcdistance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class OcclusionFilterTest {

    private static final int FRAME_SIZE = 960; // 20ms @ 48kHz
    private static final int SAMPLE_RATE = 48000;

    @BeforeEach
    void setUp() {
        OcclusionFilter.clearAll();
    }

    @Test
    @DisplayName("Zero occlusion bypass leaves audio samples completely unchanged")
    void testBypassWhenOcclusionZero() {
        UUID channelId = UUID.randomUUID();
        short[] pcm = createSineWave(1000.0, 0.5, FRAME_SIZE);
        short[] original = pcm.clone();

        short[] processed = OcclusionFilter.processPcm(channelId, pcm, 0.0, 1.0);

        assertSame(pcm, processed, "Must process in-place");
        assertArrayEquals(original, processed, "Samples must be identical when occlusion = 0.0");
    }

    @Test
    @DisplayName("Zero occlusion strength bypasses filter")
    void testBypassWhenStrengthZero() {
        UUID channelId = UUID.randomUUID();
        short[] pcm = createSineWave(1000.0, 0.5, FRAME_SIZE);
        short[] original = pcm.clone();

        short[] processed = OcclusionFilter.processPcm(channelId, pcm, 1.0, 0.0);

        assertSame(pcm, processed, "Must process in-place");
        assertArrayEquals(original, processed, "Samples must be identical when strength = 0.0");
    }

    @Test
    @DisplayName("High frequency 8kHz tone is heavily attenuated under full occlusion")
    void testHighFrequencyAttenuation() {
        UUID channelId = UUID.randomUUID();
        double freq = 8000.0;
        short[] pcm = createSineWave(freq, 0.8, FRAME_SIZE * 5); // 100ms

        double energyBefore = calculateRms(pcm);

        OcclusionFilter.processPcm(channelId, pcm, 1.0, 1.0);

        // Analyze last 20ms frame after filter settling
        short[] steadyState = new short[FRAME_SIZE];
        System.arraycopy(pcm, FRAME_SIZE * 4, steadyState, 0, FRAME_SIZE);
        double energyAfter = calculateRms(steadyState);

        assertTrue(energyAfter < energyBefore * 0.20,
                "8kHz tone should be attenuated by at least 80% under full occlusion. Before: "
                        + energyBefore + ", After: " + energyAfter);
    }

    @Test
    @DisplayName("Low frequency 150Hz bass passes through barrier with minimal attenuation")
    void testLowFrequencyPassesThrough() {
        UUID channelId = UUID.randomUUID();
        double freq = 150.0;
        short[] pcm = createSineWave(freq, 0.8, FRAME_SIZE * 5);

        double energyBefore = calculateRms(pcm);

        OcclusionFilter.processPcm(channelId, pcm, 1.0, 1.0);

        short[] steadyState = new short[FRAME_SIZE];
        System.arraycopy(pcm, FRAME_SIZE * 4, steadyState, 0, FRAME_SIZE);
        double energyAfter = calculateRms(steadyState);

        // Low bass should retain >60% of original energy through solid barrier (targetGain 0.75 - lowpass ~0.9)
        assertTrue(energyAfter > energyBefore * 0.55,
                "150Hz bass should mostly pass through barrier. Before: " + energyBefore + ", After: " + energyAfter);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.25, 0.5, 0.75, 1.0})
    @DisplayName("Cutoff frequency scales monotonically between 18kHz and 500Hz")
    void testCutoffFrequencyRange(double factor) {
        double fc = OcclusionFilter.calculateCutoffFrequency(factor);
        assertTrue(fc >= OcclusionFilter.MIN_CUTOFF_HZ - 0.01 && fc <= OcclusionFilter.MAX_CUTOFF_HZ + 0.01,
                "Cutoff (" + fc + " Hz) must be within [500Hz, 18000Hz]");

        if (factor == 0.0) {
            assertEquals(OcclusionFilter.MAX_CUTOFF_HZ, fc, 0.1);
        } else if (factor == 1.0) {
            assertEquals(OcclusionFilter.MIN_CUTOFF_HZ, fc, 0.1);
        }
    }

    @Test
    @DisplayName("Filter maintains continuity across frames without boundary clicks/pops")
    void testFrameContinuity() {
        UUID channelId = UUID.randomUUID();
        short[] frame1 = createSineWave(400.0, 0.9, FRAME_SIZE);
        short[] frame2 = createSineWaveOffset(400.0, 0.9, FRAME_SIZE, FRAME_SIZE);

        OcclusionFilter.processPcm(channelId, frame1, 0.8, 1.0);
        OcclusionFilter.processPcm(channelId, frame2, 0.8, 1.0);

        int lastSampleFrame1 = frame1[FRAME_SIZE - 1];
        int firstSampleFrame2 = frame2[0];
        int discontinuity = Math.abs(firstSampleFrame2 - lastSampleFrame1);

        // Sine wave at 400Hz @ 48kHz changes by max ~2 * pi * 400 / 48000 * 30000 ≈ 1570 per sample.
        // A pop/click would produce an abrupt discontinuity of > 10000.
        assertTrue(discontinuity < 3000,
                "Sample discontinuity across frame boundary should be smooth (< 3000), but was: " + discontinuity);
    }

    @Test
    @DisplayName("Channel reset clears filter state without crashing")
    void testChannelReset() {
        UUID channelId = UUID.randomUUID();
        short[] frame = createSineWave(1000.0, 0.5, FRAME_SIZE);
        OcclusionFilter.processPcm(channelId, frame, 0.8, 1.0);

        assertDoesNotThrow(() -> OcclusionFilter.reset(channelId));
        assertDoesNotThrow(() -> OcclusionFilter.reset(null));
        assertDoesNotThrow(() -> OcclusionFilter.clearAll());
    }

    @Test
    @DisplayName("Null and empty inputs are handled safely")
    void testEdgeCases() {
        assertNull(OcclusionFilter.processPcm(null, null, 0.5, 1.0));
        short[] empty = new short[0];
        assertSame(empty, OcclusionFilter.processPcm(UUID.randomUUID(), empty, 0.5, 1.0));
    }

    // --- Helpers ---

    private short[] createSineWave(double freqHz, double amplitude, int length) {
        return createSineWaveOffset(freqHz, amplitude, length, 0);
    }

    private short[] createSineWaveOffset(double freqHz, double amplitude, int length, int sampleOffset) {
        short[] pcm = new short[length];
        double maxAmp = 32767.0 * amplitude;
        for (int i = 0; i < length; i++) {
            int t = sampleOffset + i;
            pcm[i] = (short) Math.round(maxAmp * Math.sin(2.0 * Math.PI * freqHz * t / SAMPLE_RATE));
        }
        return pcm;
    }

    private double calculateRms(short[] pcm) {
        double sum = 0.0;
        for (short s : pcm) {
            sum += (double) s * s;
        }
        return Math.sqrt(sum / pcm.length);
    }
}
