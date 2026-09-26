package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class EnvironmentTest {

    private static short[] noise(Random r, int n, int amplitude) {
        short[] s = new short[n];
        for (int i = 0; i < n; i++) {
            s[i] = (short) ((r.nextDouble() * 2 - 1) * amplitude);
        }
        return s;
    }

    private static double rms(short[] s) {
        double sum = 0;
        for (short v : s) {
            sum += (double) v * v;
        }
        return Math.sqrt(sum / s.length);
    }

    @Test
    @DisplayName("Reverb leaves audio untouched when off")
    void reverbOff() {
        Reverb reverb = new Reverb();
        short[] frame = noise(new Random(1), 960, 8000);
        short[] copy = frame.clone();
        reverb.process(frame, 0.0, 1.5);
        assertArrayEquals(copy, frame);
        assertFalse(reverb.isActive());
    }

    @Test
    @DisplayName("Reverb adds a tail that fades, longer for a longer decay, at a sane level")
    void reverbTail() {
        double shortTail = tailAfter(0.4);
        double longTail = tailAfter(2.5);
        assertTrue(longTail > shortTail * 3, "long " + longTail + " vs short " + shortTail);

        // The echo of steady speech is audible but not louder than the voice itself
        Reverb reverb = new Reverb();
        Random r = new Random(3);
        double dry = 0;
        double out = 0;
        for (int f = 0; f < 100; f++) {
            short[] in = noise(r, 960, 6000);
            short[] frame = in.clone();
            reverb.process(frame, 1.0, 2.5);
            if (f > 50) {
                short[] diff = new short[in.length];
                for (int i = 0; i < in.length; i++) {
                    diff[i] = (short) (frame[i] - in[i]);
                }
                dry += rms(in);
                out += rms(diff);
            }
        }
        double ratio = out / dry;
        assertTrue(ratio > 0.2 && ratio < 1.2, "echo/voice " + ratio);
    }

    /** RMS of the echo 0.5 s after a burst of noise stops. */
    private static double tailAfter(double decay) {
        Reverb reverb = new Reverb();
        Random r = new Random(2);
        for (int f = 0; f < 10; f++) {
            reverb.process(noise(r, 960, 8000), 1.0, decay);
        }
        double tail = 0;
        for (int f = 0; f < 50; f++) {
            short[] silence = new short[960];
            reverb.process(silence, 1.0, decay);
            if (f >= 25 && f < 30) {
                tail += rms(silence);
            }
        }
        return tail;
    }

    @Test
    @DisplayName("Reverb stays stable and switches itself off once turned down")
    void reverbStableAndStops() {
        Reverb reverb = new Reverb();
        Random r = new Random(4);
        for (int f = 0; f < 500; f++) {
            short[] frame = noise(r, 960, 30000);
            reverb.process(frame, 1.0, Reverb.MAX_DECAY_SECONDS);
            for (short v : frame) {
                assertTrue(v > Short.MIN_VALUE - 1 && v < Short.MAX_VALUE + 1);
            }
        }
        assertTrue(reverb.isActive());
        for (int f = 0; f < 400 && reverb.isActive(); f++) {
            reverb.process(new short[960], 0.0, 1.0);
        }
        assertFalse(reverb.isActive());
    }

    /**
     * Rays from a listener 1.62 above the floor inside a box: side walls at {@code halfX} / {@code halfZ}
     * (infinite: none), a ceiling {@code ceiling} above the eyes (negative: open sky).
     */
    private static RoomEstimate.Hit[] box(double halfX, double halfZ, double ceiling, AcousticMaterial walls,
                                          AcousticMaterial floor) {
        RoomEstimate.Hit[] hits = new RoomEstimate.Hit[RoomEstimate.DIRECTIONS.length];
        for (int i = 0; i < hits.length; i++) {
            double[] d = RoomEstimate.DIRECTIONS[i];
            double best = Double.MAX_VALUE;
            AcousticMaterial m = null;
            if (Math.abs(d[0]) > 1e-9 && halfX / Math.abs(d[0]) < best) {
                best = halfX / Math.abs(d[0]);
                m = walls;
            }
            if (Math.abs(d[2]) > 1e-9 && halfZ / Math.abs(d[2]) < best) {
                best = halfZ / Math.abs(d[2]);
                m = walls;
            }
            if (d[1] > 1e-9 && ceiling >= 0 && ceiling / d[1] < best) {
                best = ceiling / d[1];
                m = walls;
            }
            if (d[1] < -1e-9 && 1.62 / -d[1] < best) {
                best = 1.62 / -d[1];
                m = floor;
            }
            hits[i] = best <= RoomEstimate.rayLength(i) ? new RoomEstimate.Hit(best, m) : null;
        }
        return hits;
    }

    private static final double NONE = Double.POSITIVE_INFINITY;

    @Test
    @DisplayName("Echo: open ground, a forest and a wool room stay dry")
    void dryPlaces() {
        RoomEstimate field = RoomEstimate.of(box(NONE, NONE, -1, AcousticMaterial.STONE, AcousticMaterial.EARTH));
        assertEquals(0.0, field.wet(), 1e-9);
        assertTrue(field.echoes().isEmpty());
        assertEquals(RoomEstimate.Kind.OPEN, field.kind());

        // Trunks and leaves all around and overhead
        RoomEstimate forest = RoomEstimate.of(box(4, 5, 4, AcousticMaterial.LEAVES, AcousticMaterial.EARTH));
        assertEquals(0.0, forest.wet(), 1e-9);
        assertEquals(RoomEstimate.Kind.FOREST, forest.kind());

        RoomEstimate wool = RoomEstimate.of(box(3, 3, 1.5, AcousticMaterial.WOOL, AcousticMaterial.WOOL));
        assertEquals(0.0, wool.wet(), 1e-9);
        assertEquals(RoomEstimate.Kind.DEAD, wool.kind());
        assertFalse(wool.isAudible());
    }

    @Test
    @DisplayName("Echo: a stone room rings briefly, a wooden house warmly, a cave long")
    void echoingPlaces() {
        RoomEstimate stone = RoomEstimate.of(box(2.5, 2.5, 1.4, AcousticMaterial.STONE, AcousticMaterial.STONE));
        RoomEstimate wood = RoomEstimate.of(box(3.5, 3.5, 2.4, AcousticMaterial.WOOD, AcousticMaterial.WOOD));
        RoomEstimate cave = RoomEstimate.of(box(15, 12, 10, AcousticMaterial.STONE, AcousticMaterial.STONE));

        assertEquals(RoomEstimate.Kind.SMALL, stone.kind());
        assertEquals(RoomEstimate.Kind.WOODEN, wood.kind());
        assertEquals(RoomEstimate.Kind.LARGE, cave.kind());
        assertTrue(stone.decaySeconds() > 0.4 && stone.decaySeconds() < 1.8, "stone room " + stone.decaySeconds());
        assertTrue(wood.decaySeconds() < stone.decaySeconds() && wood.decaySeconds() < 0.8, "wood " + wood.decaySeconds());
        assertTrue(cave.decaySeconds() > 2.0, "cave " + cave.decaySeconds());
        assertTrue(wood.damping() > stone.damping(), "wood sounds warmer");
        assertTrue(stone.wet() > wood.wet() && cave.wet() > stone.wet());
        assertTrue(cave.wet() <= 1.0);
        assertTrue(cave.preDelaySeconds() > stone.preDelaySeconds());
        // Early reflections come in order, each a distinct delay
        assertFalse(stone.early().isEmpty());
        for (int i = 1; i < stone.early().size(); i++) {
            assertTrue(stone.early().delays()[i] > stone.early().delays()[i - 1]);
        }
        // No distinct repeats under a roof
        assertTrue(cave.echoes().isEmpty());

        RoomEstimate tunnel = RoomEstimate.of(box(1.5, NONE, 0.4, AcousticMaterial.STONE, AcousticMaterial.STONE));
        assertEquals(RoomEstimate.Kind.TUNNEL, tunnel.kind());
        assertTrue(tunnel.wet() > 0.0);
    }

    @Test
    @DisplayName("Echo in the mountains: far cliffs repeat the voice, a canyon once more; a lone trunk does not")
    void mountainEcho() {
        // Cliffs 25 blocks away on one side, 40 on the other, open sky
        RoomEstimate.Hit[] hits = box(NONE, NONE, -1, AcousticMaterial.STONE, AcousticMaterial.EARTH);
        for (int i = 0; i < RoomEstimate.RING; i++) {
            double[] d = RoomEstimate.DIRECTIONS[i];
            if (Math.abs(d[0]) > 0.3) {
                double t = (d[0] > 0 ? 25.0 : 40.0) / Math.abs(d[0]);
                if (t <= RoomEstimate.ECHO_RAY_LENGTH) {
                    hits[i] = new RoomEstimate.Hit(t, AcousticMaterial.STONE);
                }
            }
        }
        RoomEstimate canyon = RoomEstimate.of(hits);
        assertEquals(RoomEstimate.Kind.CANYON, canyon.kind());
        assertEquals(3, canyon.echoes().size());
        assertEquals(2 * 25.0 / 343.0, canyon.echoes().delays()[0], 1e-3);
        assertEquals(2 * 40.0 / 343.0, canyon.echoes().delays()[1], 1e-3);
        assertEquals(2 * 65.0 / 343.0, canyon.echoes().delays()[2], 1e-3);
        assertTrue(canyon.echoes().gains()[0] > canyon.echoes().gains()[1]);
        assertTrue(canyon.isAudible());

        // One tree trunk 20 blocks away: a single ray, wood
        RoomEstimate.Hit[] tree = box(NONE, NONE, -1, AcousticMaterial.STONE, AcousticMaterial.EARTH);
        tree[0] = new RoomEstimate.Hit(20.0, AcousticMaterial.WOOD);
        tree[1] = new RoomEstimate.Hit(20.5, AcousticMaterial.WOOD);
        assertTrue(RoomEstimate.of(tree).echoes().isEmpty());
        // A lone boulder hit by one ray only
        RoomEstimate.Hit[] boulder = box(NONE, NONE, -1, AcousticMaterial.STONE, AcousticMaterial.EARTH);
        boulder[3] = new RoomEstimate.Hit(30.0, AcousticMaterial.STONE);
        assertTrue(RoomEstimate.of(boulder).echoes().isEmpty());
    }

    @Test
    @DisplayName("Echo: close voices stay clear, far ones sound like the room; spaces combine; zones and gliding")
    void echoShareAndCombine() {
        RoomEstimate cave = RoomEstimate.of(box(15, 12, 10, AcousticMaterial.STONE, AcousticMaterial.STONE));
        assertTrue(cave.distanceShare(1.0) < 0.1);
        assertTrue(cave.distanceShare(20.0) > 0.8);
        assertTrue(cave.distanceShare(5.0) > cave.distanceShare(2.0));
        assertEquals(0.5, cave.distanceShare(-1.0), 1e-9);

        // A friend in a cave, the listener outside: the voice echoes
        RoomEstimate field = RoomEstimate.of(box(NONE, NONE, -1, AcousticMaterial.STONE, AcousticMaterial.EARTH));
        RoomEstimate both = RoomEstimate.combine(field, cave);
        assertEquals(cave.wet(), both.wet(), 1e-9);
        assertSame(field, RoomEstimate.combine(field, null));
        RoomEstimate room = RoomEstimate.of(box(2.5, 2.5, 1.4, AcousticMaterial.STONE, AcousticMaterial.STONE));
        RoomEstimate mixed = RoomEstimate.combine(room, cave);
        assertTrue(mixed.wet() > cave.wet() && mixed.wet() <= 1.0);
        assertTrue(mixed.decaySeconds() > room.decaySeconds() && mixed.decaySeconds() < cave.decaySeconds());

        RoomEstimate half = field.towards(cave, 0.5);
        assertEquals((field.wet() + cave.wet()) / 2, half.wet(), 1e-9);
        assertEquals(RoomEstimate.OPEN, RoomEstimate.of(null));
        assertEquals(0.0, RoomEstimate.forced(0.0).wet(), 1e-9);
        assertTrue(RoomEstimate.forced(1.0).decaySeconds() > RoomEstimate.forced(0.2).decaySeconds());
        for (double[] d : RoomEstimate.DIRECTIONS) {
            assertEquals(1.0, Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]), 1e-9);
        }
    }

    @Test
    @DisplayName("Reverb: a repeat off a cliff arrives exactly 2d/343 s later, at its level")
    void reverbRepeat() {
        RoomEstimate.Taps echo = new RoomEstimate.Taps(new double[]{0.1}, new double[]{0.5});
        RoomEstimate cliff = new RoomEstimate(RoomEstimate.Kind.CANYON, 0.2, 2.0, Reverb.MIN_DECAY_SECONDS, 0.0,
                0.3, 0.0, RoomEstimate.Taps.NONE, echo);
        Reverb reverb = new Reverb();
        // Let the level glide in first
        for (int f = 0; f < 40; f++) {
            reverb.process(new short[960], cliff, 0.0, 1.0);
        }
        short[] impulse = new short[960];
        impulse[0] = 20000;
        reverb.process(impulse, cliff, 0.0, 1.0);
        assertEquals(20000, impulse[0]);
        for (int f = 1; f <= 5; f++) {
            short[] frame = new short[960];
            reverb.process(frame, cliff, 0.0, 1.0);
            for (int i = 0; i < frame.length; i++) {
                if (f == 5 && i == 0) {
                    assertEquals(10000, frame[i], 150);
                } else {
                    assertEquals(0, frame[i], "silence before the repeat, frame " + f + " sample " + i);
                }
            }
        }
    }

    @Test
    @DisplayName("Water muffles both ways; rain covers far voices more; effects combine")
    void effects() {
        assertEquals(EnvironmentEffects.Effect.NONE, EnvironmentEffects.water(false, false));
        EnvironmentEffects.Effect water = EnvironmentEffects.water(true, false);
        assertEquals(water, EnvironmentEffects.water(false, true));
        assertTrue(OcclusionModel.cutoffHz(water.muffle()) < 800);

        EnvironmentEffects.Effect near = EnvironmentEffects.weather(EnvironmentEffects.Weather.RAIN, 0.1);
        EnvironmentEffects.Effect far = EnvironmentEffects.weather(EnvironmentEffects.Weather.RAIN, 1.0);
        EnvironmentEffects.Effect storm = EnvironmentEffects.weather(EnvironmentEffects.Weather.THUNDER, 1.0);
        assertTrue(near.lossDb() < far.lossDb() && far.lossDb() < storm.lossDb());
        assertEquals(EnvironmentEffects.Effect.NONE, EnvironmentEffects.weather(EnvironmentEffects.Weather.CLEAR, 1.0));

        EnvironmentEffects.Effect both = water.plus(far);
        assertEquals(water.lossDb() + far.lossDb(), both.lossDb(), 1e-9);
        assertTrue(both.muffle() > water.muffle() && both.muffle() < 1.0);
    }
}
