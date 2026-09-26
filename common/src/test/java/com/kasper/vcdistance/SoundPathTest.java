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

    /** A solid block x 1..10, z -10..4, y -3..3: a building to go round. */
    private static World building() {
        World w = new World();
        for (int x = 1; x <= 10; x++) {
            w.wall(x, -3, 3, -10, 4);
        }
        return w;
    }

    @Test
    @DisplayName("Round a right-angled corner the way turns about 90 degrees and muffles more than a slight bend")
    void cornerAngle() {
        World w = building();
        // Listener west of the building, speaker north of it: round the north-west corner
        SoundPath.Result right = SoundPath.find(w, 0.5, 0.5, 0.5, 5.5, 0.5, 5.5, 40, 20000);
        assertNotNull(right);
        // On the block grid a corner may take two cells, each turning part of the way
        assertTrue(right.corners() >= 1 && right.corners() <= 2, "corners " + right.corners());
        assertTrue(right.turn() > Math.toRadians(55) && right.turn() < Math.toRadians(125), "turn " + Math.toDegrees(right.turn()));
        assertTrue(right.length() > right.direct() + 1.0, "length " + right.length() + " direct " + right.direct());
        // The voice comes from the corner
        assertTrue(right.openingX() < 1.6 && right.openingZ() > 3.9, "opening " + right.openingX() + ", " + right.openingZ());

        // Far to the west, almost in line with the north wall: only a slight bend
        SoundPath.Result slight = SoundPath.find(w, -10.5, 0.5, 3.5, 5.5, 0.5, 5.5, 40, 20000);
        assertNotNull(slight);
        assertTrue(slight.corners() >= 1);
        assertTrue(slight.turn() < right.turn() / 2, "slight " + Math.toDegrees(slight.turn()));
        assertTrue(slight.thickness() < right.thickness());
        assertTrue(right.thickness() > 0.3 && right.thickness() < 1.0, "right angle " + right.thickness());
    }

    private static final java.util.function.DoubleUnaryOperator CURVE = d -> Math.max(0.0, 1.0 - d / 48.0);

    @Test
    @DisplayName("Blend: no way round is just the wall; a clear doorway takes over; a long way round hardly counts")
    void blend() {
        SoundBlend none = SoundBlend.of(10.0, 2.0, Double.NaN, Double.NaN, 0.6, CURVE);
        assertEquals(0.0, none.pathShare(), 1e-9);
        assertEquals(10.0, none.distance(), 1e-9);
        assertEquals(OcclusionModel.muffle(2.0, 0.6), none.muffle(), 1e-9);
        assertEquals(OcclusionModel.lossDb(2.0, 0.6), none.lossDb(), 1e-9);

        // Thick stone wall, a doorway a little way round
        SoundBlend door = SoundBlend.of(10.0, 3.0, 13.0, 0.4, 0.6, CURVE);
        assertTrue(door.pathShare() > 0.8, "share " + door.pathShare());
        assertTrue(door.muffle() < OcclusionModel.muffle(3.0, 0.6));
        assertTrue(door.distance() > 12.0 && door.distance() <= 13.0);
        assertTrue(door.mostlyRound());
        assertEquals(door.distance() - 10.0, door.extraDistance(), 1e-9);

        // A glass pane in the way, the way round long and bent
        SoundBlend glass = SoundBlend.of(10.0, 0.4, 35.0, 1.2, 0.6, CURVE);
        assertTrue(glass.pathShare() < 0.2, "share " + glass.pathShare());
        assertFalse(glass.mostlyRound());
        // Adding a way round never makes the voice quieter than the wall alone
        assertTrue(glass.lossDb() <= OcclusionModel.lossDb(0.4, 0.6) + 1e-9);
        assertTrue(door.lossDb() <= OcclusionModel.lossDb(3.0, 0.6) + 1e-9);
    }

    @Test
    @DisplayName("Blend changes smoothly as the way round gets clearer: no flip at the point where they are equal")
    void blendIsContinuous() {
        double last = -1.0;
        double lastMuffle = -1.0;
        for (double t = 3.0; t >= 0.0; t -= 0.05) {
            SoundBlend b = SoundBlend.of(10.0, 1.5, 10.0, t, 0.6, CURVE);
            if (last >= 0.0) {
                assertTrue(b.pathShare() >= last - 1e-9, "share grows as the way round clears");
                assertTrue(b.pathShare() - last < 0.08, "no jump at " + t);
                assertTrue(Math.abs(b.muffle() - lastMuffle) < 0.08, "muffle glides at " + t);
            }
            last = b.pathShare();
            lastMuffle = b.muffle();
        }
        assertEquals(0.5, SoundBlend.of(10.0, 1.5, 10.0, 1.5, 0.6, CURVE).pathShare(), 1e-9);
    }
}
