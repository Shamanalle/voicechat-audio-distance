package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SoundPathTest {

    /** A floor-less box world: everything open except the listed solid blocks. */
    private static final class World implements SoundPath.Grid {
        final Set<Long> solid = new HashSet<>();

        void wall(int x, int y1, int y2, int z1, int z2) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    solid.add(k(x, y, z));
                }
            }
        }

        void hole(int x, int y, int z) {
            solid.remove(k(x, y, z));
        }

        @Override
        public boolean open(int x, int y, int z) {
            return !solid.contains(k(x, y, z));
        }

        static long k(int x, int y, int z) {
            return ((long) x << 40) ^ ((long) y << 20) ^ z;
        }
    }

    @Test
    @DisplayName("Open line of sight: the path is the straight line and the voice comes from the speaker")
    void open() {
        World w = new World();
        SoundPath.Result r = SoundPath.find(w, 0.5, 0.5, 0.5, 8.5, 0.5, 0.5, 30, 2000);
        assertNotNull(r);
        assertEquals(8.0, r.direct(), 1e-9);
        assertEquals(8.0, r.length(), 0.01);
        assertEquals(8.5, r.openingX(), 1e-9);
    }

    @Test
    @DisplayName("Wall with a doorway: the way around is found and the voice comes from the doorway")
    void doorway() {
        World w = new World();
        // A wall at x = 4 from z -10..10, y -5..5, with a doorway at z = 5
        w.wall(4, -5, 5, -10, 10);
        w.hole(4, 0, 5);
        w.hole(4, 1, 5);
        SoundPath.Result r = SoundPath.find(w, 0.5, 0.5, 0.5, 8.5, 0.5, 0.5, 40, 5000);
        assertNotNull(r);
        assertTrue(r.length() > r.direct() + 2, "detour " + r.detour());
        assertTrue(r.length() < 20, "length " + r.length());
        // The listener hears it from the doorway side
        assertTrue(r.openingZ() > 3.0, "opening z " + r.openingZ());
        assertTrue(r.openingX() <= 5.0, "opening x " + r.openingX());
        assertTrue(r.thickness() < 1.0, "a doorway muffles less than a stone wall: " + r.thickness());
    }

    @Test
    @DisplayName("Sealed wall or a way that is too long: nothing is found")
    void sealed() {
        World w = new World();
        w.wall(4, -5, 5, -10, 10);
        // Surround the whole thing so sound cannot go over or around within the budget
        assertNull(SoundPath.find(w, 0.5, 0.5, 0.5, 8.5, 0.5, 0.5, 12, 5000));
        w.hole(4, 0, 9);
        assertNull(SoundPath.find(w, 0.5, 0.5, 0.5, 8.5, 0.5, 0.5, 12, 5000), "doorway is too far for the limit");
        assertNotNull(SoundPath.find(w, 0.5, 0.5, 0.5, 8.5, 0.5, 0.5, 30, 20000));
    }

    @Test
    @DisplayName("No squeezing through a corner where both sides are closed")
    void corners() {
        World w = new World();
        // Two solid blocks meeting at an edge between the cells (0,0,0) and (1,0,1)
        w.solid.add(World.k(1, 0, 0));
        w.solid.add(World.k(0, 0, 1));
        // Close everything else around so only the diagonal would connect them
        for (int x = -1; x <= 2; x++) {
            for (int z = -1; z <= 2; z++) {
                for (int y : new int[]{-1, 1}) {
                    w.solid.add(World.k(x, y, z));
                }
            }
        }
        for (int i = -1; i <= 2; i++) {
            w.solid.add(World.k(i, 0, -1));
            w.solid.add(World.k(i, 0, 2));
            w.solid.add(World.k(-1, 0, i));
            w.solid.add(World.k(2, 0, i));
        }
        assertNull(SoundPath.find(w, 0.5, 0.5, 0.5, 1.5, 0.5, 1.5, 10, 500));
    }
}
