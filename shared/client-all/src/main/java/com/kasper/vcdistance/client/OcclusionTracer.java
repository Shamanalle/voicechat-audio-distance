package com.kasper.vcdistance.client;

import net.minecraft.world.phys.Vec3;

/**
 * Casts a small bundle of parallel rays between listener and speaker and averages their acoustic
 * thickness. A single ray flips between "clear" and "fully blocked" on door frames and corners;
 * five rays spread over roughly one block give soft, believable edges.
 */
public final class OcclusionTracer {

    private static final double SPREAD = 0.4;
    private static final double MIN_DISTANCE = 0.5;

    private OcclusionTracer() {
    }

    public static double trace(WorldAccess access, Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
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

        double total = access.traceRay(from, to);
        total += offsetRay(access, from, to, sx * SPREAD, sy * SPREAD, sz * SPREAD);
        total += offsetRay(access, from, to, -sx * SPREAD, -sy * SPREAD, -sz * SPREAD);
        total += offsetRay(access, from, to, ux * SPREAD, uy * SPREAD, uz * SPREAD);
        total += offsetRay(access, from, to, -ux * SPREAD, -uy * SPREAD, -uz * SPREAD);
        return total / 5.0;
    }

    private static double offsetRay(WorldAccess access, Vec3 from, Vec3 to, double ox, double oy, double oz) {
        return access.traceRay(
                new Vec3(from.x + ox, from.y + oy, from.z + oz),
                new Vec3(to.x + ox, to.y + oy, to.z + oz));
    }
}
