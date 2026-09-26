package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RayGeometryTest {

    private static List<int[]> walk(double fx, double fy, double fz, double tx, double ty, double tz) {
        List<int[]> blocks = new ArrayList<>();
        VoxelRay.walk(fx, fy, fz, tx, ty, tz, 1000, (x, y, z) -> {
            blocks.add(new int[]{x, y, z});
            return false;
        });
        return blocks;
    }

    @Test
    @DisplayName("Voxel walk visits every block along an axis, start and end included")
    void walkAlongAxis() {
        List<int[]> blocks = walk(0.5, 64.5, 0.5, 5.5, 64.5, 0.5);
        assertEquals(6, blocks.size());
        for (int i = 0; i < 6; i++) {
            assertArrayEquals(new int[]{i, 64, 0}, blocks.get(i));
        }
        List<int[]> back = walk(-0.5, 10.5, -2.5, -3.5, 10.5, -2.5);
        assertArrayEquals(new int[]{-1, 10, -3}, back.get(0));
        assertArrayEquals(new int[]{-4, 10, -3}, back.get(back.size() - 1));
        assertEquals(4, back.size());
    }

    @Test
    @DisplayName("Voxel walk on a diagonal visits a connected path of face neighbours")
    void walkDiagonal() {
        List<int[]> blocks = walk(0.2, 0.3, 0.4, 7.9, 3.1, -4.6);
        assertArrayEquals(new int[]{0, 0, 0}, blocks.get(0));
        assertArrayEquals(new int[]{7, 3, -5}, blocks.get(blocks.size() - 1));
        for (int i = 1; i < blocks.size(); i++) {
            int[] a = blocks.get(i - 1);
            int[] b = blocks.get(i);
            int steps = Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);
            assertEquals(1, steps, "blocks must share a face");
        }
        // 7 steps in x, 3 in y, 5 in z
        assertEquals(1 + 7 + 3 + 5, blocks.size());
    }

    @Test
    @DisplayName("Voxel walk stops when the visitor asks and respects the block limit")
    void walkStops() {
        int[] count = {0};
        VoxelRay.walk(0.5, 0.5, 0.5, 100.5, 0.5, 0.5, 1000, (x, y, z) -> ++count[0] == 3);
        assertEquals(3, count[0]);
        count[0] = 0;
        VoxelRay.walk(0.5, 0.5, 0.5, 100.5, 0.5, 0.5, 10, (x, y, z) -> {
            count[0]++;
            return false;
        });
        assertEquals(10, count[0]);
    }

    @Test
    @DisplayName("Segment/box test sees slabs by their real height")
    void boxIntersection() {
        // Bottom slab at block (2, 0, 0): y 0..0.5
        assertTrue(VoxelRay.intersects(0.5, 0.25, 0.5, 4.5, 0.25, 0.5, 2, 0, 0, 3, 0.5, 1));
        assertFalse(VoxelRay.intersects(0.5, 0.75, 0.5, 4.5, 0.75, 0.5, 2, 0, 0, 3, 0.5, 1));
        // Segment ending before the box
        assertFalse(VoxelRay.intersects(0.5, 0.25, 0.5, 1.5, 0.25, 0.5, 2, 0, 0, 3, 0.5, 1));
        // Starting inside counts
        assertTrue(VoxelRay.intersects(2.5, 0.25, 0.5, 9.5, 5, 5, 2, 0, 0, 3, 0.5, 1));
        // Diagonal passing over the corner
        assertFalse(VoxelRay.intersects(0, 2, 0, 4, 0.9, 0, 2, 0, 0, 3, 0.5, 1));
    }

    @Test
    @DisplayName("Ray bundle averages five parallel rays around the direct one")
    void rayBundle() {
        List<double[]> rays = new ArrayList<>();
        double avg = RayBundle.trace((fx, fy, fz, tx, ty, tz) -> {
            rays.add(new double[]{fx, fy, fz, tx, ty, tz});
            return rays.size() == 1 ? 5.0 : 0.0;
        }, 0, 64, 0, 10, 64, 0);
        assertEquals(5, rays.size());
        assertEquals(1.0, avg, 1e-9);
        for (double[] r : rays) {
            // Parallel to the direct ray and within the spread
            assertEquals(r[3] - r[0], 10.0, 1e-9);
            assertEquals(r[4] - r[1], 0.0, 1e-9);
            assertTrue(Math.abs(r[1] - 64) <= 0.4 + 1e-9 && Math.abs(r[2]) <= 0.4 + 1e-9);
        }
        // Straight up still works, and very short rays are free
        assertEquals(0.0, RayBundle.trace((a, b, c, d, e, f) -> 1.0, 0, 0, 0, 0, 0.2, 0));
        assertEquals(1.0, RayBundle.trace((a, b, c, d, e, f) -> 1.0, 0, 0, 0, 0, 20, 0), 1e-9);
    }

    /** Thickness along a ray through a block world: full blocks, each weighted by the chord it runs inside. */
    private static double blocks(java.util.function.Predicate<int[]> solid, double fx, double fy, double fz,
                                 double tx, double ty, double tz) {
        double[] t = {0.0};
        VoxelRay.walk(fx, fy, fz, tx, ty, tz, 1000, (x, y, z) -> {
            if (solid.test(new int[]{x, y, z})) {
                t[0] += RayBundle.chordWeight(VoxelRay.chord(fx, fy, fz, tx, ty, tz, x, y, z, x + 1, y + 1, z + 1));
            }
            return false;
        });
        return t[0];
    }

    @Test
    @DisplayName("Two players side by side in a 2-high corridor hear each other clearly")
    void corridorStaysClear() {
        // Floor at y = 63, air at 64 and 65, ceiling at 66; walls at z = -1 and z = 1 (1-wide tunnel)
        java.util.function.Predicate<int[]> solid = b -> b[1] <= 63 || b[1] >= 66 || b[2] <= -1 || b[2] >= 1;
        SoundPath.Grid open = (x, y, z) -> !solid.test(new int[]{x, y, z});
        RayBundle.RayCaster caster = (fx, fy, fz, tx, ty, tz) -> blocks(solid, fx, fy, fz, tx, ty, tz);
        // Eyes at 64 + 1.62, the listener hugging the wall at z = 0.3
        double without = RayBundle.trace(caster, 0.5, 65.62, 0.3, 6.5, 65.62, 0.5);
        double with = RayBundle.trace(caster, open, 0.5, 65.62, 0.3, 6.5, 65.62, 0.5);
        assertTrue(without > 0.5, "the old side rays ran inside the ceiling and wall: " + without);
        assertEquals(0.0, with, 1e-9);
        // A real wall across the corridor still counts in full
        java.util.function.Predicate<int[]> walled = b -> solid.test(b) || b[0] == 3;
        SoundPath.Grid open2 = (x, y, z) -> !walled.test(new int[]{x, y, z});
        double wall = RayBundle.trace((fx, fy, fz, tx, ty, tz) -> blocks(walled, fx, fy, fz, tx, ty, tz), open2,
                0.5, 65.62, 0.5, 6.5, 65.62, 0.5);
        assertEquals(1.0, wall, 1e-9);
    }

    @Test
    @DisplayName("A ray grazing a block's corner counts less than one crossing it")
    void chordWeighting() {
        // Straight through a block: 1 block inside
        assertEquals(1.0, VoxelRay.chord(-1, 0.5, 0.5, 2, 0.5, 0.5, 0, 0, 0, 1, 1, 1), 1e-9);
        assertEquals(1.0, RayBundle.chordWeight(1.0), 1e-9);
        // Diagonal through the very corner: a short chord
        double corner = VoxelRay.chord(-1, 2.9, 0.5, 3, -1.1, 0.5, 0, 0, 0, 1, 1, 1);
        assertTrue(corner > 0.0 && corner < 0.2, "corner chord " + corner);
        assertTrue(RayBundle.chordWeight(corner) < 0.5);
        // Missing the block entirely
        assertEquals(0.0, VoxelRay.chord(-1, 1.5, 0.5, 2, 1.5, 0.5, 0, 0, 0, 1, 1, 1), 1e-9);
        assertEquals(0.0, RayBundle.chordWeight(0.0), 1e-9);
    }
}
