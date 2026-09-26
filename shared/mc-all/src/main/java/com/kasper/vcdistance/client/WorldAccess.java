package com.kasper.vcdistance.client;

import com.kasper.vcdistance.EnvironmentEffects;
import com.kasper.vcdistance.NearbyPlayers;
import com.kasper.vcdistance.RayBundle;
import com.kasper.vcdistance.RoomEstimate;
import com.kasper.vcdistance.SpeakerRegistry;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The few world operations that differ between Minecraft versions. Every method is called on the
 * client (main) thread only.
 */
public interface WorldAccess {

    /** Maximum acoustic thickness a single ray accumulates before it stops tracing. */
    double MAX_RAY_THICKNESS = RayBundle.MAX_RAY_THICKNESS;

    boolean inWorld();

    /** Identity of the current world object; a change means a new world or server was joined. */
    Object worldIdentity();

    /** Where the listener hears from (the same point Simple Voice Chat uses). */
    Vec3 listenerPosition();

    /** Where the listener looks, as Minecraft yaw in degrees ({@link com.kasper.vcdistance.Bearing}). */
    double listenerYaw();

    /**
     * Resolves the mouth position of an entity speaker and fills in its display name.
     *
     * @return the position, or {@code null} when the entity is not loaded
     */
    Vec3 entitySpeakerPosition(SpeakerRegistry.Speaker speaker, long nowNanos);

    /**
     * Other players within {@code range} blocks of {@code listener} (eye to eye) whom the local
     * player can see, with their direction: spectators and invisible players are left out.
     */
    List<NearbyPlayers.Player> nearbyPlayers(Vec3 listener, double range);

    /**
     * The first solid block along a direction (unit vector) and its material, or {@code null} when
     * nothing is hit within {@code maxDistance}. Measures the space a voice echoes in.
     */
    RoomEstimate.Hit rayHit(Vec3 from, double dx, double dy, double dz, double maxDistance);

    /** {@code true} when sound passes this block freely: air, water, open doors and gates, fences, bars. */
    boolean isOpenForSound(int x, int y, int z);

    /** {@code true} when this point is inside water. */
    boolean isUnderWater(Vec3 point);

    /** Rain or thunder falling on this point (under the open sky, in a biome where it rains). */
    EnvironmentEffects.Weather weatherAt(Vec3 point);

    /** Acoustic thickness (in stone blocks) along one straight ray. */
    double traceRay(Vec3 from, Vec3 to);

    /** Drops caches that depend on the current world or server. */
    void reset();
}
