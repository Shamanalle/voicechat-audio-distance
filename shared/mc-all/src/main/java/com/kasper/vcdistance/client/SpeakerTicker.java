package com.kasper.vcdistance.client;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.DistanceConfig;
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

    private final WorldAccess access;
    private int lastMaterialRevision = Integer.MIN_VALUE;
    private Object lastWorld;
    private boolean loggedFailure;

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
            for (SpeakerRegistry.Speaker s : active) {
                s.setDistance(-1.0);
                s.clearOcclusion();
            }
            return;
        }

        boolean tracing = AudioDistancePlugin.occlusionStatus() == AudioDistancePlugin.OcclusionStatus.ACTIVE;
        Vec3 listener = access.listenerPosition();
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

    private void reset() {
        AudioDistancePlugin.SPEAKERS.clear();
        access.reset();
    }
}
