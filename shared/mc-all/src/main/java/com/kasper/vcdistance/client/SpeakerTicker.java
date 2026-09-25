package com.kasper.vcdistance.client;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.Bearing;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.ListenerEnvironment;
import com.kasper.vcdistance.RoomEstimate;
import com.kasper.vcdistance.SpeakerRegistry;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs every client tick on the main thread: resolves where each active voice comes from and
 * measures the walls in between. Audio threads only read the results.
 */
public final class SpeakerTicker {

    private static final long TRACE_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private static final int MAX_TRACES_PER_TICK = 12;
    /** The room around the listener is measured this often (18 rays). */
    private static final int ROOM_INTERVAL_TICKS = 10;

    private final WorldAccess access;
    private int lastMaterialRevision = Integer.MIN_VALUE;
    private Object lastWorld;
    private boolean loggedFailure;
    private boolean loggedNearbyFailure;
    private boolean loggedEnvironmentFailure;
    private int ticks;

    public SpeakerTicker(WorldAccess access) {
        this.access = access;
        AudioDistancePlugin.markOcclusionProviderAvailable();
    }

    public WorldAccess access() {
        return access;
    }

    /** Never throws: a failure here must not take the client tick down with it. */
    public void tick() {
        try {
            tickUnsafe();
        } catch (Throwable t) {
            if (!loggedFailure) {
                loggedFailure = true;
                DistanceConfig.LOGGER.warn("Voice distance tick failed, wall muffling paused: {}", t.toString());
            }
        }
    }

    private void tickUnsafe() {
        Object world = access.worldIdentity();
        if (world != lastWorld) {
            // Joined, left or switched world/server: forget everything tied to the old one
            lastWorld = world;
            reset();
        }

        long now = System.nanoTime();
        SpeakerRegistry registry = AudioDistancePlugin.SPEAKERS;
        registry.prune(now);

        DistanceConfig config = AudioDistancePlugin.config();
        int revision = System.identityHashCode(config) * 31 + config.getRevision();
        boolean configChanged = revision != lastMaterialRevision;
        lastMaterialRevision = revision;

        List<SpeakerRegistry.Speaker> active = registry.active(now);
        if (!access.inWorld()) {
            AudioDistancePlugin.NEARBY.clear();
            AudioDistancePlugin.ENVIRONMENT.reset();
            for (SpeakerRegistry.Speaker s : active) {
                s.setDistance(-1.0);
                s.clearOcclusion();
            }
            return;
        }

        boolean tracing = AudioDistancePlugin.occlusionStatus() == AudioDistancePlugin.OcclusionStatus.ACTIVE;
        Vec3 listener = access.listenerPosition();
        double yaw = access.listenerYaw();
        updateNearby(listener);
        updateEnvironment(listener);
        int budget = MAX_TRACES_PER_TICK;

        for (SpeakerRegistry.Speaker s : active) {
            try {
                Vec3 source = s.getKind() == SpeakerRegistry.Kind.ENTITY
                        ? access.entitySpeakerPosition(s, now)
                        : new Vec3(s.getSoundX(), s.getSoundY(), s.getSoundZ());
                if (source == null || listener == null) {
                    s.setDistance(-1.0);
                    s.clearOcclusion();
                    continue;
                }
                s.setDistance(listener.distanceTo(source));
                s.setBearing(Bearing.relative(source.x - listener.x, source.z - listener.z, yaw));
                s.setSurroundings(access.isUnderWater(source), access.weatherAt(source));

                if (!tracing) {
                    s.clearOcclusion();
                } else if (budget > 0 && (configChanged || !s.isOcclusionKnown()
                        || now - s.getLastTraceNanos() >= TRACE_INTERVAL_NANOS)) {
                    s.setOcclusion(OcclusionTracer.trace(access::traceRay, listener, source), now);
                    budget--;
                }
            } catch (Throwable t) {
                DistanceConfig.LOGGER.debug("Failed to update speaker {}: {}", s.getChannelId(), t.toString());
                s.clearOcclusion();
            }
        }
    }

    /** The monitor's list of nearby players; a failure here must not stop the wall tracing. */
    private void updateNearby(Vec3 listener) {
        try {
            AudioDistancePlugin.NEARBY.update(access.nearbyPlayers(listener, AudioDistancePlugin.getServerMaxDistance()));
        } catch (Throwable t) {
            AudioDistancePlugin.NEARBY.clear();
            if (!loggedNearbyFailure) {
                loggedNearbyFailure = true;
                DistanceConfig.LOGGER.warn("Could not list nearby players for the monitor: {}", t.toString());
            }
        }
    }

    /** Water and weather every tick, the echo of the room every half second. */
    private void updateEnvironment(Vec3 listener) {
        try {
            ListenerEnvironment env = AudioDistancePlugin.ENVIRONMENT;
            if (listener == null) {
                env.reset();
                return;
            }
            env.update(access.isUnderWater(listener), access.weatherAt(listener));
            if (ticks++ % ROOM_INTERVAL_TICKS == 0) {
                double[] hits = new double[RoomEstimate.DIRECTIONS.length];
                for (int i = 0; i < hits.length; i++) {
                    double[] d = RoomEstimate.DIRECTIONS[i];
                    hits[i] = access.rayDistance(listener, d[0], d[1], d[2], RoomEstimate.RAY_LENGTH);
                }
                env.updateRoom(RoomEstimate.of(hits));
            }
        } catch (Throwable t) {
            AudioDistancePlugin.ENVIRONMENT.reset();
            if (!loggedEnvironmentFailure) {
                loggedEnvironmentFailure = true;
                DistanceConfig.LOGGER.warn("Could not measure the surroundings, echo and water are paused: {}", t.toString());
            }
        }
    }

    private void reset() {
        AudioDistancePlugin.SPEAKERS.clear();
        AudioDistancePlugin.NEARBY.clear();
        AudioDistancePlugin.ENVIRONMENT.reset();
        access.reset();
    }
}
