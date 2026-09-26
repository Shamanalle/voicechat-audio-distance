package com.kasper.vcdistance;

/**
 * Casts a small bundle of parallel rays between listener and speaker and averages their acoustic
 * thickness. A single ray flips between "clear" and "fully blocked" on door frames and corners;
 * five rays spread over roughly one block give soft, believable edges.
 * <p>
 * The side rays must stay in the air the voice travels through: in a two-block corridor a ray
 * 0.4 above the eyes would run inside the ceiling the whole way. When a side ray would start or
 * end inside a solid block, its shift is halved, then dropped.
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
    /** A ray this far inside a full block counts it fully; one that only grazes a corner counts less. */
    public static final double FULL_CHORD = 0.4;
    private static final double MIN_DISTANCE = 0.5;

    private RayBundle() {
    }

    /** Share of a full block's weight for a ray running {@code chord} blocks inside it. */
    public static double chordWeight(double chord) {
        return chord <= 0.0 ? 0.0 : Math.min(1.0, chord / FULL_CHORD);
    }

    public static double trace(RayCaster caster, double fromX, double fromY, double fromZ,
                               double toX, double toY, double toZ) {
        return trace(caster, null, fromX, fromY, fromZ, toX, toY, toZ);
    }

    /**
     * @param open which blocks are air for sound, to keep the side rays out of walls and ceilings;
     *             {@code null} shifts them the full spread
     */
    public static double trace(RayCaster caster, SoundPath.Grid open, double fromX, double fromY, double fromZ,
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
        total += offset(caster, open, fromX, fromY, fromZ, toX, toY, toZ, sx, sy, sz);
        total += offset(caster, open, fromX, fromY, fromZ, toX, toY, toZ, -sx, -sy, -sz);
        total += offset(caster, open, fromX, fromY, fromZ, toX, toY, toZ, ux, uy, uz);
        total += offset(caster, open, fromX, fromY, fromZ, toX, toY, toZ, -ux, -uy, -uz);
        return total / 5.0;
    }

    /** One side ray, shifted along the unit vector {@code (ox, oy, oz)} as far as the air allows. */
    private static double offset(RayCaster caster, SoundPath.Grid open, double fromX, double fromY, double fromZ,
                                 double toX, double toY, double toZ, double ox, double oy, double oz) {
        double spread = SPREAD;
        if (open != null) {
            while (spread > 0.0 && !(inAir(open, fromX + ox * spread, fromY + oy * spread, fromZ + oz * spread)
                    && inAir(open, toX + ox * spread, toY + oy * spread, toZ + oz * spread))) {
                spread = spread > SPREAD * 0.3 ? spread / 2.0 : 0.0;
            }
        }
        return caster.cast(fromX + ox * spread, fromY + oy * spread, fromZ + oz * spread,
                toX + ox * spread, toY + oy * spread, toZ + oz * spread);
    }

    private static boolean inAir(SoundPath.Grid open, double x, double y, double z) {
        return open.open((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }
}
