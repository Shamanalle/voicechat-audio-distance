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
}
