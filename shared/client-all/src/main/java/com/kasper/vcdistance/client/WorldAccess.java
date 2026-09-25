package com.kasper.vcdistance.client;

import com.kasper.vcdistance.SpeakerRegistry;
import net.minecraft.world.phys.Vec3;

/**
 * The few world operations that differ between Minecraft versions. Every method is called on the
 * client (main) thread only.
 */
public interface WorldAccess {

    /** Maximum acoustic thickness a single ray accumulates before it stops tracing. */
    double MAX_RAY_THICKNESS = 8.0;

    boolean inWorld();

    /** Identity of the current world object; a change means a new world or server was joined. */
    Object worldIdentity();

    /** Where the listener hears from (the same point Simple Voice Chat uses). */
    Vec3 listenerPosition();

    /**
     * Resolves the mouth position of an entity speaker and fills in its display name.
     *
     * @return the position, or {@code null} when the entity is not loaded
     */
    Vec3 entitySpeakerPosition(SpeakerRegistry.Speaker speaker, long nowNanos);

    /** Acoustic thickness (in stone blocks) along one straight ray. */
    double traceRay(Vec3 from, Vec3 to);

    /** Drops caches that depend on the current world or server. */
    void reset();
}
