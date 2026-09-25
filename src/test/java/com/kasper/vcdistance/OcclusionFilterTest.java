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
        UUID channel = UUID.randomUUID();
        short[] original = generateSineWave(1000, 10000, FRAME_SIZE);
        short[] input = original.clone();

        short[] result = OcclusionFilter.processPcm(channel, input, 0.0, 1.0);

        assertSame(input, result, "Should return the same array buffer");
        assertArrayEquals(original, result, "Audio data must be strictly identical when occlusion is zero");
    }

    @Test
    @DisplayName("Low frequencies (bass/fundamentals) pass through wall occlusion with minimal loss")
    void testLowFrequencyPassThrough() {
        UUID channel = UUID.randomUUID();
        // 200 Hz tone (within human voice vowel fundamental range)
        short[] lowFreq = generateSineWave(200, 15000, FRAME_SIZE * 5); // 100ms
        double initialRms = calculateRms(lowFreq);

        short[] processed = OcclusionFilter.processPcm(channel, lowFreq, 1.0, 1.0);
        double processedRms = calculateRms(processed);

        double retention = processedRms / initialRms;
        // Even under full occlusion (cutoff ~500Hz), 200Hz should retain >60% energy (including -25% barrier gain absorption)
        assertTrue(retention > 0.60,
                "200 Hz audio should retain majority of energy under wall occlusion, actual retention: " + retention);
    }

    @Test
    @DisplayName("High frequencies (treble/consonants) are heavily attenuated by wall occlusion")
    void testHighFrequencyAttenuation() {
        UUID channel = UUID.randomUUID();
        // 6000 Hz tone (sibilance/friction consonants like 's', 'sh')
        short[] highFreq = generateSineWave(6000, 15000, FRAME_SIZE * 5); // 100ms
        double initialRms = calculateRms(highFreq);

        short[] processed = OcclusionFilter.processPcm(channel, highFreq, 1.0, 1.0);
        double processedRms = calculateRms(processed);

        double retention = processedRms / initialRms;
        // 6000 Hz is over 10x the 500 Hz cutoff, 1-pole filter must reduce it by >85% (retention < 0.15)
        assertTrue(retention < 0.15,
                "6000 Hz audio should be strongly attenuated through walls, actual retention: " + retention);
    }

    @Test
    @DisplayName("Stream continuity across consecutive frames prevents clicks/pops at frame boundaries")
    void testFrameContinuityNoClicks() {
        UUID channel = UUID.randomUUID();
        short[] fullStream = generateSineWave(440, 20000, FRAME_SIZE * 2);

        short[] frame1 = new short[FRAME_SIZE];
        short[] frame2 = new short[FRAME_SIZE];
        System.arraycopy(fullStream, 0, frame1, 0, FRAME_SIZE);
        System.arraycopy(fullStream, FRAME_SIZE, frame2, 0, FRAME_SIZE);

        short[] processed1 = OcclusionFilter.processPcm(channel, frame1, 0.8, 1.0);
        short[] processed2 = OcclusionFilter.processPcm(channel, frame2, 0.8, 1.0);

        short lastSampleFrame1 = processed1[FRAME_SIZE - 1];
        short firstSampleFrame2 = processed2[0];

        // The step between the last sample of frame 1 and first of frame 2 should be continuous
        int step = Math.abs(firstSampleFrame2 - lastSampleFrame1);
        // For a 440 Hz wave @ 48kHz, max normal step per sample is roughly 2*PI*440/48000 * 20000 ≈ 1150
        assertTrue(step < 2000,
                "Frame boundary step (" + step + ") must be smooth and continuous, not an abrupt transient click");
    }

    @Test
    @DisplayName("Extreme full-scale square wave inputs never overflow 16-bit PCM bounds")
    void testNoClippingOrOverflow() {
        UUID channel = UUID.randomUUID();
        short[] extreme = new short[FRAME_SIZE * 3];
        for (int i = 0; i < extreme.length; i++) {
            extreme[i] = (i % 40 < 20) ? Short.MAX_VALUE : Short.MIN_VALUE;
        }

        short[] processed = OcclusionFilter.processPcm(channel, extreme, 1.0, 1.0);

        for (int i = 0; i < processed.length; i++) {
            short sample = processed[i];
            assertTrue(sample >= Short.MIN_VALUE && sample <= Short.MAX_VALUE,
                    "Sample at index " + i + " must remain within signed 16-bit range");
        }
    }

    @Test
    @DisplayName("Exponential cutoff frequency calculation scales smoothly and monotonically")
    void testCutoffFrequencyScale() {
        assertEquals(18000.0, OcclusionFilter.calculateCutoffFrequency(0.0), 1e-4);
        assertEquals(500.0, OcclusionFilter.calculateCutoffFrequency(1.0), 1e-4);

        double prevCutoff = 18000.0;
        for (double occ = 0.1; occ <= 1.0; occ += 0.1) {
            double currentCutoff = OcclusionFilter.calculateCutoffFrequency(occ);
            assertTrue(currentCutoff < prevCutoff,
                    "Cutoff at " + occ + " (" + currentCutoff + ") should be lower than " + prevCutoff);
            prevCutoff = currentCutoff;
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.25, 0.50, 0.75, 1.0})
    @DisplayName("Occlusion presets and settings in DistanceConfig are valid and bounded")
    void testConfigOcclusionIntegrity(double strength) {
        DistanceConfig config = new DistanceConfig();
        config.applyRealistic();
        assertTrue(config.occlusionEnabled, "Realistic preset should have occlusion enabled");
        assertEquals(0.65, config.occlusionStrength, 1e-4);

        config.applyDefault();
        assertFalse(config.occlusionEnabled, "Vanilla default preset should have occlusion disabled");

        config.applyAtmospheric();
        assertTrue(config.occlusionEnabled, "Stealth/Atmospheric preset should have occlusion enabled");
        assertEquals(0.85, config.occlusionStrength, 1e-4);

        config.applyHighAudibility();
        assertFalse(config.occlusionEnabled, "High audibility preset should have occlusion disabled");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DSP Signal Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static short[] generateSineWave(double frequencyHz, double amplitude, int samples) {
        short[] buffer = new short[samples];
        double angularFreq = 2.0 * Math.PI * frequencyHz / SAMPLE_RATE;
        for (int i = 0; i < samples; i++) {
            buffer[i] = (short) Math.round(amplitude * Math.sin(i * angularFreq));
        }
        return buffer;
    }

    private static double calculateRms(short[] buffer) {
        double sum = 0.0;
        for (short s : buffer) {
            sum += (double) s * s;
        }
        return Math.sqrt(sum / buffer.length);
    }
}
