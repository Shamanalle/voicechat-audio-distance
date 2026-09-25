package com.kasper.vcdistance.server;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.ServerWalls;
import com.kasper.vcdistance.client.BlockAcoustics;
import com.kasper.vcdistance.client.OcclusionTracer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Measures walls between a listener and a speaker on the server (Minecraft 1.20 - 1.21.x), with the
 * same multi-ray tracer the client uses and the server profile's material weights.
 */
public final class ServerThickness implements ServerWalls.ThicknessProvider {

    /** Beyond this nobody hears proximity voice anyway; do not trace across the map. */
    private static final double MAX_TRACE_DISTANCE = 160.0;

    @Override
    public double thickness(Object listener, Object levelObject, UUID speakerEntity, double x, double y, double z) {
        // The level comes from Simple Voice Chat: the accessor for it is named differently across versions
        if (!(listener instanceof ServerPlayer player) || !(levelObject instanceof ServerLevel level)) {
            return Double.NaN;
        }
        Vec3 ear = player.getEyePosition();
        Vec3 source;
        if (speakerEntity != null) {
            // Looked up in the listener's own level, so both are always in the same dimension
            Entity speaker = level.getPlayerByUUID(speakerEntity);
            if (speaker == null) {
                double r = MAX_TRACE_DISTANCE / 2.0;
                List<Entity> found = level.getEntities((Entity) null,
                        new AABB(ear.x - r, ear.y - r, ear.z - r, ear.x + r, ear.y + r, ear.z + r),
                        e -> speakerEntity.equals(e.getUUID()));
                speaker = found.isEmpty() ? null : found.get(0);
            }
            if (speaker == null) {
                return Double.NaN;
            }
            source = speaker.getEyePosition();
        } else {
            source = new Vec3(x, y, z);
        }
        if (ear.distanceTo(source) > MAX_TRACE_DISTANCE) {
            return Double.NaN;
        }
        return OcclusionTracer.trace(
                (from, to) -> BlockAcoustics.traceRay(level, from, to, AudioDistancePlugin.SERVER_SETTINGS.profile()),
                ear, source);
    }
}
