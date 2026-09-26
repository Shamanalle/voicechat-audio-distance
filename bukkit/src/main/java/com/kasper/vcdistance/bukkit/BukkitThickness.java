package com.kasper.vcdistance.bukkit;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.RayBundle;
import com.kasper.vcdistance.ServerWalls;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Measures walls between a listener and a speaker on Bukkit servers, with the same five-ray bundle
 * and material weights as the Fabric builds. Runs on the main thread.
 */
final class BukkitThickness implements ServerWalls.ThicknessProvider {

    /** Beyond this nobody hears proximity voice anyway; do not trace across the map. */
    private static final double MAX_TRACE_DISTANCE = 160.0;

    @Override
    public double thickness(Object listener, Object levelObject, UUID speakerEntity, double x, double y, double z) {
        // Simple Voice Chat on Bukkit hands out Bukkit players and worlds
        if (!(listener instanceof Player player) || !(levelObject instanceof World world)) {
            return Double.NaN;
        }
        Location ear = player.getEyeLocation();
        if (!world.equals(ear.getWorld())) {
            return Double.NaN;
        }
        double sx = x;
        double sy = y;
        double sz = z;
        if (speakerEntity != null) {
            Entity speaker = Bukkit.getEntity(speakerEntity);
            if (speaker == null || !world.equals(speaker.getWorld())) {
                return Double.NaN;
            }
            Location mouth = speaker instanceof LivingEntity living ? living.getEyeLocation() : speaker.getLocation();
            sx = mouth.getX();
            sy = mouth.getY();
            sz = mouth.getZ();
        }
        double dx = sx - ear.getX();
        double dy = sy - ear.getY();
        double dz = sz - ear.getZ();
        if (dx * dx + dy * dy + dz * dz > MAX_TRACE_DISTANCE * MAX_TRACE_DISTANCE) {
            return Double.NaN;
        }
        return RayBundle.trace(
                (fx, fy, fz, tx, ty, tz) -> BlockAcoustics.traceRay(world, fx, fy, fz, tx, ty, tz,
                        AudioDistancePlugin.SERVER_SETTINGS.profile()),
                (bx, by, bz) -> BlockAcoustics.isOpenForSound(world, bx, by, bz),
                ear.getX(), ear.getY(), ear.getZ(), sx, sy, sz);
    }
}
