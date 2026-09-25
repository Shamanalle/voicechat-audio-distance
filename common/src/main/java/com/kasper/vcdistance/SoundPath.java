package com.kasper.vcdistance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Sound going around walls: the shortest way from the speaker to the listener through open blocks
 * (air, water, open doors, fences). When a wall is in the straight line but a doorway is nearby,
 * the voice reaches you through the doorway: a little further, much less muffled, and from the
 * doorway's direction.
 * <p>
 * The search is an A* over block cells with face and edge moves (no squeezing through a corner
 * where both sides are closed), bounded by a path length and a node budget, so it stays cheap.
 */
public final class SoundPath {

    /** Which blocks sound passes through freely. Coordinates are block coordinates. */
    @FunctionalInterface
    public interface Grid {
        boolean open(int x, int y, int z);
    }

    /**
     * A path that was found.
     *
     * @param length  length of the way around, in blocks
     * @param direct  straight-line distance, in blocks
     * @param opening the point the sound seems to come from: the furthest point of the path the
     *                listener can see directly (the doorway, for a voice in the next room)
     */
    public record Result(double length, double direct, double openingX, double openingY, double openingZ) {

        /** How much further the way around is than the straight line. */
        public double detour() {
            return Math.max(0.0, length - direct);
        }

        /**
         * Muffling of the way around, in stone blocks: sound bending round an edge loses some
         * highs, and more the longer the detour. Compare with the straight line's wall thickness.
         */
        public double thickness() {
            return BEND_THICKNESS + DETOUR_THICKNESS * detour();
        }
    }

    static final double BEND_THICKNESS = 0.3;
    static final double DETOUR_THICKNESS = 0.05;

    /** 18 moves: 6 faces and 12 edges. */
    private static final int[][] MOVES = moves();

    private SoundPath() {
    }

    /**
     * @param maxLength longest way around worth finding, in blocks
     * @param maxNodes  most cells to look at before giving up
     * @return the way around, or {@code null} when there is none within the limits
     */
    public static Result find(Grid grid, double lx, double ly, double lz, double sx, double sy, double sz,
                              double maxLength, int maxNodes) {
        int[] start = cell(lx, ly, lz);
        int[] goal = cell(sx, sy, sz);
        double direct = Math.sqrt(sq(sx - lx) + sq(sy - ly) + sq(sz - lz));
        if (!grid.open(start[0], start[1], start[2]) || !grid.open(goal[0], goal[1], goal[2])) {
            return null;
        }

        Map<Long, Node> seen = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Node first = new Node(start[0], start[1], start[2], 0.0, heuristic(start, goal), null);
        seen.put(key(start[0], start[1], start[2]), first);
        open.add(first);
        int visited = 0;
        while (!open.isEmpty()) {
            Node n = open.poll();
            if (n.closed) {
                continue;
            }
            n.closed = true;
            if (n.x == goal[0] && n.y == goal[1] && n.z == goal[2]) {
                return result(grid, n, lx, ly, lz, sx, sy, sz, direct);
            }
            if (++visited > maxNodes) {
                return null;
            }
            for (int[] m : MOVES) {
                int x = n.x + m[0];
                int y = n.y + m[1];
                int z = n.z + m[2];
                if (!grid.open(x, y, z)) {
                    continue;
                }
                // An edge move needs one of the two faces it passes to be open
                if (m[3] == 1 && !passable(grid, n, m)) {
                    continue;
                }
                double g = n.g + (m[3] == 1 ? Math.sqrt(2.0) : 1.0);
                double f = g + heuristic(new int[]{x, y, z}, goal);
                if (f > maxLength + 1.0) {
                    continue;
                }
                long k = key(x, y, z);
                Node old = seen.get(k);
                if (old != null && (old.closed || old.g <= g)) {
                    continue;
                }
                Node next = new Node(x, y, z, g, f, n);
                seen.put(k, next);
                open.add(next);
            }
        }
        return null;
    }

    private static Result result(Grid grid, Node end, double lx, double ly, double lz,
                                 double sx, double sy, double sz, double direct) {
        List<Node> path = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent) {
            path.add(0, n);
        }
        // The furthest point along the path (from the listener) still in direct sight
        double ox = sx;
        double oy = sy;
        double oz = sz;
        if (!visible(grid, lx, ly, lz, sx, sy, sz)) {
            ox = path.get(0).x + 0.5;
            oy = path.get(0).y + 0.5;
            oz = path.get(0).z + 0.5;
            for (int i = path.size() - 1; i > 0; i--) {
                Node n = path.get(i);
                if (visible(grid, lx, ly, lz, n.x + 0.5, n.y + 0.5, n.z + 0.5)) {
                    ox = n.x + 0.5;
                    oy = n.y + 0.5;
                    oz = n.z + 0.5;
                    break;
                }
            }
        }
        // Path length between the real end points: cells in between, plus the partial first and last cells
        double length = end.g;
        if (path.size() > 1) {
            Node second = path.get(1);
            Node beforeLast = path.get(path.size() - 2);
            length += Math.sqrt(sq(second.x + 0.5 - lx) + sq(second.y + 0.5 - ly) + sq(second.z + 0.5 - lz))
                    - Math.sqrt(sq(second.x - path.get(0).x) + sq(second.y - path.get(0).y) + sq(second.z - path.get(0).z));
            length += Math.sqrt(sq(beforeLast.x + 0.5 - sx) + sq(beforeLast.y + 0.5 - sy) + sq(beforeLast.z + 0.5 - sz))
                    - Math.sqrt(sq(end.x - beforeLast.x) + sq(end.y - beforeLast.y) + sq(end.z - beforeLast.z));
        }
        return new Result(Math.max(direct, length), direct, ox, oy, oz);
    }

    /** {@code true} when every block on the straight line is open. */
    static boolean visible(Grid grid, double fx, double fy, double fz, double tx, double ty, double tz) {
        boolean[] blocked = {false};
        VoxelRay.walk(fx, fy, fz, tx, ty, tz, 256, (x, y, z) -> {
            if (!grid.open(x, y, z)) {
                blocked[0] = true;
                return true;
            }
            return false;
        });
        return !blocked[0];
    }

    private static boolean passable(Grid grid, Node n, int[] m) {
        // The edge move changes two axes; stepping along either one first must be open
        int ax = m[0];
        int ay = m[1];
        int az = m[2];
        if (ax != 0 && ay != 0) {
            return grid.open(n.x + ax, n.y, n.z) || grid.open(n.x, n.y + ay, n.z);
        }
        if (ax != 0 && az != 0) {
            return grid.open(n.x + ax, n.y, n.z) || grid.open(n.x, n.y, n.z + az);
        }
        return grid.open(n.x, n.y + ay, n.z) || grid.open(n.x, n.y, n.z + az);
    }

    private static double heuristic(int[] a, int[] b) {
        return Math.sqrt(sq(a[0] - b[0]) + sq(a[1] - b[1]) + sq(a[2] - b[2]));
    }

    private static int[] cell(double x, double y, double z) {
        return new int[]{(int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)};
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0xFFFFF) << 22) | (z & 0x3FFFFF);
    }

    private static double sq(double v) {
        return v * v;
    }

    private static int[][] moves() {
        List<int[]> list = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int axes = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (axes == 1 || axes == 2) {
                        list.add(new int[]{dx, dy, dz, axes == 2 ? 1 : 0});
                    }
                }
            }
        }
        return list.toArray(new int[0][]);
    }

    private static final class Node {
        final int x;
        final int y;
        final int z;
        final double g;
        final double f;
        final Node parent;
        boolean closed;

        Node(int x, int y, int z, double g, double f, Node parent) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.g = g;
            this.f = f;
            this.parent = parent;
        }
    }
}
