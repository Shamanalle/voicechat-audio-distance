package com.kasper.vcdistance;

/**
 * Casts a small bundle of parallel rays between listener and speaker and averages their acoustic
 * thickness. A single ray flips between "clear" and "fully blocked" on door frames and corners;
 * five rays spread over roughly one block give soft, believable edges.
 * <p>
 * Shared by the client and server tracers of every Minecraft version and of the Bukkit plugin.
 */
public final class RayBundle {

    /** Acoustic thickness (in stone blocks) along one straight ray. */
    @FunctionalInterface
    public interface RayCaster {
        double cast(double fromX, double fromY, double fromZ, double toX, double toY, double toZ);
    }

    /** A single ray stops counting past this thickness: the voice is fully muffled anyway. */
    public static final double MAX_RAY_THICKNESS = 8.0;

    private static final double SPREAD = 0.4;
    private static final double MIN_DISTANCE = 0.5;

    private RayBundle() {
    }

    public static double trace(RayCaster caster, double fromX, double fromY, double fromZ,
                               double toX, double toY, double toZ) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < MIN_DISTANCE) {
            return 0.0;
        }
        dx /= length;
        dy /= length;
        dz /= length;

        // side = dir x up; falls back to the X axis when looking straight up or down
        double sx = -dz;
        double sy = 0.0;
        double sz = dx;
        double sideLength = Math.sqrt(sx * sx + sz * sz);
        if (sideLength < 1e-4) {
            sx = 1.0;
            sz = 0.0;
            sideLength = 1.0;
        }
        sx /= sideLength;
        sz /= sideLength;

        // up' = side x dir, perpendicular to both
        double ux = sy * dz - sz * dy;
        double uy = sz * dx - sx * dz;
        double uz = sx * dy - sy * dx;

        double total = caster.cast(fromX, fromY, fromZ, toX, toY, toZ);
        total += offset(caster, fromX, fromY, fromZ, toX, toY, toZ, sx * SPREAD, sy * SPREAD, sz * SPREAD);
        total += offset(caster, fromX, fromY, fromZ, toX, toY, toZ, -sx * SPREAD, -sy * SPREAD, -sz * SPREAD);
        total += offset(caster, fromX, fromY, fromZ, toX, toY, toZ, ux * SPREAD, uy * SPREAD, uz * SPREAD);
        total += offset(caster, fromX, fromY, fromZ, toX, toY, toZ, -ux * SPREAD, -uy * SPREAD, -uz * SPREAD);
        return total / 5.0;
    }

    private static double offset(RayCaster caster, double fromX, double fromY, double fromZ,
                                 double toX, double toY, double toZ, double ox, double oy, double oz) {
        return caster.cast(fromX + ox, fromY + oy, fromZ + oz, toX + ox, toY + oy, toZ + oz);
    }
}
