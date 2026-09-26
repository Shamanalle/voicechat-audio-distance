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
 * The path found on the block grid is pulled tight round its corners, so its length is the real
 * way round and the angle it turns says how much the voice is muffled bending round the edges.
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
     * A path that was found, pulled tight round the corners it has to take.
     *
     * @param length  length of the way around, in blocks, between the real end points
     * @param direct  straight-line distance, in blocks
     * @param opening the point the sound seems to come from: the first corner of the way round, which
     *                the listener sees directly (the doorway, for a voice in the next room)
     * @param turn    how far the way turns in total, in radians (0 in plain sight, about 1.57 round a
     *                right-angled corner, about 3.14 back round a wall)
     * @param corners how many corners it goes round
     */
    public record Result(double length, double direct, double openingX, double openingY, double openingZ,
                         double turn, int corners) {

        /** How much further the way around is than the straight line. */
        public double detour() {
            return Math.max(0.0, length - direct);
        }

        /**
         * Muffling of the way around, in stone blocks: sound bending round an edge loses its highs, the
         * more the sharper the bend (a slight bend hardly, a right angle like half a wall, back round a
         * wall like a whole one). Compare with the straight line's wall thickness.
         */
        public double thickness() {
            return TURN_THICKNESS * turn / (Math.PI / 2.0) + DETOUR_THICKNESS * detour();
        }
    }

    /** Muffling of a right-angled bend, in stone blocks. */
    static final double TURN_THICKNESS = 0.6;
    static final double DETOUR_THICKNESS = 0.02;

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
        List<double[]> points = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent) {
            points.add(0, new double[]{n.x + 0.5, n.y + 0.5, n.z + 0.5});
        }
        // The real end points instead of their cells' centres
        points.set(0, new double[]{lx, ly, lz});
        if (points.size() == 1) {
            points.add(new double[]{sx, sy, sz});
        } else {
            points.set(points.size() - 1, new double[]{sx, sy, sz});
        }

        // Pull the path tight: from each corner, go straight to the furthest point still in sight
        List<double[]> tight = new ArrayList<>();
        tight.add(points.get(0));
        int i = 1;
        while (i < points.size()) {
            double[] anchor = tight.get(tight.size() - 1);
            int j = i;
            while (j + 1 < points.size() && visible(grid, anchor, points.get(j + 1))) {
                j++;
            }
            tight.add(points.get(j));
            i = j + 1;
        }

        double length = 0.0;
        double turn = 0.0;
        for (int k = 1; k < tight.size(); k++) {
            length += dist(tight.get(k - 1), tight.get(k));
            if (k + 1 < tight.size()) {
                turn += angle(tight.get(k - 1), tight.get(k), tight.get(k + 1));
            }
        }
        double[] opening = tight.size() > 2 ? tight.get(1) : tight.get(tight.size() - 1);
        return new Result(Math.max(direct, length), direct, opening[0], opening[1], opening[2], turn,
                Math.max(0, tight.size() - 2));
    }

    private static boolean visible(Grid grid, double[] a, double[] b) {
        return visible(grid, a[0], a[1], a[2], b[0], b[1], b[2]);
    }

    /** How far the way turns at {@code b}, in radians. */
    private static double angle(double[] a, double[] b, double[] c) {
        double ux = b[0] - a[0];
        double uy = b[1] - a[1];
        double uz = b[2] - a[2];
        double vx = c[0] - b[0];
        double vy = c[1] - b[1];
        double vz = c[2] - b[2];
        double lu = Math.sqrt(ux * ux + uy * uy + uz * uz);
        double lv = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (lu < 1e-9 || lv < 1e-9) {
            return 0.0;
        }
        double cos = (ux * vx + uy * vy + uz * vz) / (lu * lv);
        return Math.acos(Math.max(-1.0, Math.min(1.0, cos)));
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt(sq(a[0] - b[0]) + sq(a[1] - b[1]) + sq(a[2] - b[2]));
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
