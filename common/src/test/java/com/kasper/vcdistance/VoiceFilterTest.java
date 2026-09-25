package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VoiceFilterTest {

    private static final int FRAME = 960; // 20 ms @ 48 kHz

    /** Runs {@code frames} frames of a sine through the filter and returns the level change in dB of the last half. */
    private static double toneChangeDb(VoiceFilter filter, double hz, double muffle, double lossDb, int frames) {
        double in = 0.0;
        double out = 0.0;
        for (int f = 0; f < frames; f++) {
            short[] pcm = sine(hz, f, 8000);
            short[] dry = pcm.clone();
            filter.process(pcm, muffle, lossDb);
            if (f >= frames / 2) {
                for (int i = 0; i < FRAME; i++) {
                    in += (double) dry[i] * dry[i];
                    out += (double) pcm[i] * pcm[i];
                }
            }
        }
        return 10.0 * Math.log10(out / in);
    }

    private static short[] sine(double hz, int frameIndex, int amplitude) {
        short[] pcm = new short[FRAME];
        for (int i = 0; i < FRAME; i++) {
            long n = (long) frameIndex * FRAME + i;
            pcm[i] = (short) Math.round(amplitude * Math.sin(2.0 * Math.PI * hz * n / VoiceFilter.SAMPLE_RATE));
        }
        return pcm;
    }

    @Test
    @DisplayName("Without occlusion the filter is a bit-exact bypass")
    void bypassIsBitExact() {
        VoiceFilter filter = new VoiceFilter();
        short[] pcm = sine(1000, 0, 12000);
        short[] copy = pcm.clone();
        filter.process(pcm, 0.0, 0.0);
        assertArrayEquals(copy, pcm);
        assertFalse(filter.isEngaged());
    }

    @Test
    @DisplayName("After muffling ends the filter returns to a bit-exact bypass (regression)")
    void returnsToBypassAfterMuffling() {
        VoiceFilter filter = new VoiceFilter();
        toneChangeDb(filter, 1000, 0.9, 20.0, 10);
        assertTrue(filter.isEngaged());
        for (int f = 0; f < 150; f++) { // 3 s of clear line of sight
            filter.process(sine(1000, f, 8000), 0.0, 0.0);
        }
        assertFalse(filter.isEngaged(), "Filter should disengage once the wall is gone");
        short[] pcm = sine(3000, 0, 8000);
        short[] copy = pcm.clone();
        filter.process(pcm, 0.0, 0.0);
        assertArrayEquals(copy, pcm);
    }

    @Test
    @DisplayName("Heavy muffling keeps the bass and removes the treble")
    void lowPassesStrongly() {
        double muffle = OcclusionModel.muffle(3.0, 0.6);
        double loss = OcclusionModel.lossDb(3.0, 0.6);
        double bass = toneChangeDb(new VoiceFilter(), 200, muffle, loss, 40);
        double treble = toneChangeDb(new VoiceFilter(), 3000, muffle, loss, 40);
        assertEquals(-loss, bass, 1.5, "Bass should only lose the broadband loss");
        assertTrue(treble < bass - 25.0, "Treble should be cut far more than bass: bass=" + bass + " treble=" + treble);
    }

    @Test
    @DisplayName("One stone wall at default strength is clearly audible as muffled")
    void singleWallIsAudible() {
        double muffle = OcclusionModel.muffle(1.0, DistanceConfig.DEFAULT_OCCLUSION_STRENGTH);
        double loss = OcclusionModel.lossDb(1.0, DistanceConfig.DEFAULT_OCCLUSION_STRENGTH);
        double speech = toneChangeDb(new VoiceFilter(), 3000, muffle, loss, 40);
        assertTrue(speech < -12.0, "A wall should take at least 12 dB off consonants, got " + speech);
    }

    @Test
    @DisplayName("Parameter changes are smooth: no sample-to-sample jump when a wall appears")
    void noClickWhenWallAppears() {
        VoiceFilter filter = new VoiceFilter();
        short[] constant = new short[FRAME];
        java.util.Arrays.fill(constant, (short) 10000);
        filter.process(constant.clone(), 0.0, 0.0);
        short[] pcm = constant.clone();
        filter.process(pcm, 1.0, OcclusionModel.MAX_LOSS_DB);
        int maxJump = Math.abs(pcm[0] - 10000);
        for (int i = 1; i < pcm.length; i++) {
            maxJump = Math.max(maxJump, Math.abs(pcm[i] - pcm[i - 1]));
        }
        assertTrue(maxJump < 400, "Largest step between samples should be small, got " + maxJump);
    }

    @Test
    @DisplayName("Full-scale square wave never overflows")
    void noOverflow() {
        VoiceFilter filter = new VoiceFilter();
        for (int f = 0; f < 20; f++) {
            short[] pcm = new short[FRAME];
            for (int i = 0; i < FRAME; i++) {
                pcm[i] = (i / 24) % 2 == 0 ? Short.MAX_VALUE : Short.MIN_VALUE;
            }
            assertDoesNotThrow(() -> filter.process(pcm, 0.3, 0.0));
        }
    }

    @Test
    @DisplayName("Null and empty frames are ignored")
    void nullAndEmpty() {
        VoiceFilter filter = new VoiceFilter();
        assertNull(filter.process(null, 1.0, 10.0));
        assertEquals(0, filter.process(new short[0], 1.0, 10.0).length);
    }
}
