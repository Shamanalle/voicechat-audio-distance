package com.kasper.vcdistance;

/**
 * Ray geometry for tracers that have no Minecraft ray API of their own (the Bukkit plugin): walks
 * the block grid along a segment and tests the segment against block collision boxes.
 */
public final class VoxelRay {

    /** Visits one block on the ray. */
    @FunctionalInterface
    public interface Visitor {
        /** @return {@code true} to stop walking */
        boolean visit(int x, int y, int z);
    }

    private static final double EPSILON = 1e-9;

    private VoxelRay() {
    }

    /**
     * Visits every block the segment passes through, in order, starting with the block that
     * contains {@code from} and ending with the one that contains {@code to}.
     *
     * @param maxBlocks upper bound on visited blocks, as a guard against huge segments
     */
    public static void walk(double fromX, double fromY, double fromZ, double toX, double toY, double toZ,
                            int maxBlocks, Visitor visitor) {
        int x = floor(fromX);
        int y = floor(fromY);
        int z = floor(fromZ);
        int endX = floor(toX);
        int endY = floor(toY);
        int endZ = floor(toZ);

        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        int stepX = Double.compare(dx, 0.0);
        int stepY = Double.compare(dy, 0.0);
        int stepZ = Double.compare(dz, 0.0);
        // Parametric distance (0..1 along the segment) to the next grid line, and between grid lines
        double deltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double deltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        double deltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        double maxX = stepX == 0 ? Double.MAX_VALUE : deltaX * (stepX > 0 ? x + 1 - fromX : fromX - x);
        double maxY = stepY == 0 ? Double.MAX_VALUE : deltaY * (stepY > 0 ? y + 1 - fromY : fromY - y);
        double maxZ = stepZ == 0 ? Double.MAX_VALUE : deltaZ * (stepZ > 0 ? z + 1 - fromZ : fromZ - z);

        for (int visited = 0; visited < maxBlocks; visited++) {
            if (visitor.visit(x, y, z) || (x == endX && y == endY && z == endZ)) {
                return;
            }
            if (maxX < maxY && maxX < maxZ) {
                if (maxX > 1.0) {
                    return;
                }
                x += stepX;
                maxX += deltaX;
            } else if (maxY < maxZ) {
                if (maxY > 1.0) {
                    return;
                }
                y += stepY;
                maxY += deltaY;
            } else {
                if (maxZ > 1.0) {
                    return;
                }
                z += stepZ;
                maxZ += deltaZ;
            }
        }
    }

    /** Whether the segment touches the box (also when it starts or ends inside it). */
    public static boolean intersects(double fromX, double fromY, double fromZ, double toX, double toY, double toZ,
                                     double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double[] range = {0.0, 1.0};
        return clip(fromX, toX - fromX, minX, maxX, range)
                && clip(fromY, toY - fromY, minY, maxY, range)
                && clip(fromZ, toZ - fromZ, minZ, maxZ, range);
    }

    private static boolean clip(double start, double delta, double min, double max, double[] range) {
        if (Math.abs(delta) < EPSILON) {
            return start >= min && start <= max;
        }
        double a = (min - start) / delta;
        double b = (max - start) / delta;
        range[0] = Math.max(range[0], Math.min(a, b));
        range[1] = Math.min(range[1], Math.max(a, b));
        return range[0] <= range[1];
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}
