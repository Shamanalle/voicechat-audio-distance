package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SpeakerRegistryTest {

    @Test
    @DisplayName("Frames register speakers with their whisper state and level")
    void registersFrames() {
        SpeakerRegistry registry = new SpeakerRegistry();
        UUID channel = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        short[] loud = new short[960];
        java.util.Arrays.fill(loud, (short) 16384);

        SpeakerRegistry.Speaker s = registry.onEntityFrame(channel, entity, true, 24F, loud);
        assertSame(s, registry.get(channel));
        assertEquals(SpeakerRegistry.Kind.ENTITY, s.getKind());
        assertTrue(s.isWhispering());
        assertEquals(24F, s.getMaxDistance());
        assertEquals(-6.0, s.getLevelDb(), 0.1);
        assertEquals(1, registry.active(System.nanoTime()).size());
    }

    @Test
    @DisplayName("Silent sources are forgotten and active() is sorted by distance")
    void pruneAndSort() {
        SpeakerRegistry registry = new SpeakerRegistry();
        SpeakerRegistry.Speaker far = registry.onLocationalFrame(UUID.randomUUID(), 0, 0, 0, 48F, new short[960]);
        SpeakerRegistry.Speaker near = registry.onLocationalFrame(UUID.randomUUID(), 1, 1, 1, 48F, new short[960]);
        far.setDistance(30);
        near.setDistance(3);
        assertSame(near, registry.active(System.nanoTime()).get(0));

        registry.prune(System.nanoTime() + SpeakerRegistry.FORGET_AFTER_NANOS * 2);
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("Occlusion results written by the main thread are visible to readers")
    void occlusionHandOff() {
        SpeakerRegistry registry = new SpeakerRegistry();
        SpeakerRegistry.Speaker s = registry.onEntityFrame(UUID.randomUUID(), UUID.randomUUID(), false, 48F, new short[960]);
        assertFalse(s.isOcclusionKnown());
        s.setOcclusion(2.5, 1L);
        assertTrue(s.isOcclusionKnown());
        assertEquals(2.5, s.getThickness());
        s.clearOcclusion();
        assertFalse(s.isOcclusionKnown());
        assertNull(registry.get(null));
    }

    @Test
    @DisplayName("A voice moving to a doorway pans there over ~0.15 s and settles back on the straight line")
    void directionGlides() {
        SpeakerRegistry.Speaker s = new SpeakerRegistry().onEntityFrame(UUID.randomUUID(), UUID.randomUUID(), false, 48F, new short[960]);
        double[] ahead = {0, 0, 1};
        double[] right = {1, 0, 0};
        long t = 1_000_000_000L;
        // In plain sight nothing is moved
        assertNull(s.glideDirection(ahead, ahead, false, t));
        // Round a wall: starts from the straight line, not at the doorway
        double[] first = s.glideDirection(ahead, right, true, t);
        assertArrayEquals(ahead, first, 1e-9);
        double[] later = s.glideDirection(ahead, right, true, t + 50_000_000L);
        assertTrue(later[0] > 0.1 && later[2] > 0.5, "part of the way after 50 ms");
        double[] there = s.glideDirection(ahead, right, true, t + 1_000_000_000L);
        assertEquals(1.0, there[0], 0.01);
        // The way round is gone: glides back, then lets go
        double[] back = null;
        long now = t + 1_000_000_000L;
        for (int i = 0; i < 100; i++) {
            now += 20_000_000L;
            back = s.glideDirection(ahead, ahead, false, now);
            if (back == null) {
                break;
            }
        }
        assertNull(back);
        assertNull(s.glideDirection(ahead, ahead, false, now + 20_000_000L));
    }

    @Test
    @DisplayName("A way round is kept only when it bends; the path is cleared with it")
    void pathKept() {
        SpeakerRegistry.Speaker s = new SpeakerRegistry().onEntityFrame(UUID.randomUUID(), UUID.randomUUID(), false, 48F, new short[960]);
        s.setPath(new SoundPath.Result(12.0, 8.0, 1, 2, 3, Math.PI / 2, 1), 5L);
        assertTrue(s.hasOpening());
        assertEquals(12.0, s.getPathLength(), 1e-9);
        assertEquals(5L, s.getLastPathNanos());
        s.setPath(new SoundPath.Result(8.0, 8.0, 1, 2, 3, 0.0, 0), 6L);
        assertFalse(s.hasOpening());
        assertTrue(Double.isNaN(s.getPathLength()));
        assertFalse(s.isHeardRound());
    }
}
