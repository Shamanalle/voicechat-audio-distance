package com.kasper.vcdistance.client;

import com.kasper.vcdistance.RayBundle;
import com.kasper.vcdistance.SoundPath;
import net.minecraft.world.phys.Vec3;

/**
 * Minecraft-facing wrapper around {@link RayBundle}: five parallel rays between listener and
 * speaker, averaged, so walls have soft edges.
 */
public final class OcclusionTracer {

    /** Acoustic thickness (in stone blocks) along one straight ray. */
    @FunctionalInterface
    public interface RayCaster {
        double cast(Vec3 from, Vec3 to);
    }

    private OcclusionTracer() {
    }

    public static double trace(RayCaster access, Vec3 from, Vec3 to) {
        return trace(access, null, from, to);
    }

    /** @param open air for sound, keeps the side rays out of walls and ceilings ({@code null}: no check) */
    public static double trace(RayCaster access, SoundPath.Grid open, Vec3 from, Vec3 to) {
        return RayBundle.trace((fx, fy, fz, tx, ty, tz) -> access.cast(new Vec3(fx, fy, fz), new Vec3(tx, ty, tz)),
                open, from.x, from.y, from.z, to.x, to.y, to.z);
    }
}
