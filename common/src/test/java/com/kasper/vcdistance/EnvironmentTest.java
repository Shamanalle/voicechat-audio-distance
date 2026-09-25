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

    @Test
    @DisplayName("Room estimate: open air has no echo, a small room little, a big cave a long one")
    void rooms() {
        int n = RoomEstimate.DIRECTIONS.length;
        double[] open = new double[n];
        Arrays.fill(open, -1);
        open[n - 1] = 1.6; // only the ground below
        RoomEstimate air = RoomEstimate.of(open);
        assertEquals(0.0, air.wet(), 1e-9);

        double[] small = new double[n];
        Arrays.fill(small, 2.0);
        RoomEstimate room = RoomEstimate.of(small);

        double[] big = new double[n];
        Arrays.fill(big, 20.0);
        RoomEstimate cave = RoomEstimate.of(big);

        assertEquals(1.0, room.enclosure(), 1e-9);
        assertTrue(room.wet() > 0.0 && room.wet() < cave.wet());
        assertTrue(room.decaySeconds() < 0.5, "room decay " + room.decaySeconds());
        assertTrue(cave.decaySeconds() > 1.5, "cave decay " + cave.decaySeconds());
        assertTrue(cave.wet() <= 1.0);

        RoomEstimate half = air.towards(cave, 0.5);
        assertEquals((air.wet() + cave.wet()) / 2, half.wet(), 1e-9);
        assertEquals(RoomEstimate.OPEN, RoomEstimate.of(null));
        for (double[] d : RoomEstimate.DIRECTIONS) {
            assertEquals(1.0, Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]), 1e-9);
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
