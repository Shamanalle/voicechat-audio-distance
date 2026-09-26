package com.kasper.vcdistance.client;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.Bearing;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.ListenerEnvironment;
import com.kasper.vcdistance.RoomEstimate;
import com.kasper.vcdistance.SoundPath;
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
    /** A wall at least this thick (stone blocks) makes it worth looking for a way round. */
    private static final double MIN_WALL_FOR_PATH = 0.4;
    private static final long PATH_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(500);
    /** Ways round searched per tick, and how big each search may get. */
    private static final int PATHS_PER_TICK = 1;
    private static final int PATH_NODES = 1200;

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
        // A server with another voice range: fit the chosen preset to it
        AudioDistancePlugin.followServerRange();

        long start = System.nanoTime();
        try {
            work(active, now, config, configChanged);
        } finally {
            AudioDistancePlugin.CLIENT_PERF.add(System.nanoTime() - start);
            AudioDistancePlugin.CLIENT_PERF.endTick();
        }
    }

    private void work(List<SpeakerRegistry.Speaker> active, long now, DistanceConfig config, boolean configChanged) {
        // On a slow machine (or a busy spot) the same work is spread over twice the time
        int slow = AudioDistancePlugin.CLIENT_PERF.isBusy() ? 2 : 1;

        boolean tracing = AudioDistancePlugin.occlusionStatus() == AudioDistancePlugin.OcclusionStatus.ACTIVE;
        Vec3 listener = access.listenerPosition();
        double yaw = access.listenerYaw();
        updateNearby(listener);
        updateEnvironment(listener, slow);
        int budget = MAX_TRACES_PER_TICK / slow;
        int pathBudget = ticks % slow == 0 ? PATHS_PER_TICK : 0;
        boolean corners = config.isDiffractionEnabled();
        SoundPath.Grid grid = cachedGrid();

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
                        || now - s.getLastTraceNanos() >= TRACE_INTERVAL_NANOS * slow)) {
                    s.setOcclusion(OcclusionTracer.trace(access::traceRay, grid, listener, source), now);
                    budget--;
                }
                // Behind a wall: is there a doorway round it?
                if (!tracing || !corners || s.getThickness() < MIN_WALL_FOR_PATH) {
                    s.clearPath();
                } else if (pathBudget > 0 && now - s.getLastPathNanos() >= PATH_INTERVAL_NANOS * slow) {
                    pathBudget--;
                    double direct = listener.distanceTo(source);
                    double limit = Math.min(Math.max(16.0, s.getMaxDistance()), direct * 2.0 + 12.0);
                    SoundPath.Result path = SoundPath.find(grid, listener.x, listener.y, listener.z,
                            source.x, source.y, source.z, limit, PATH_NODES);
                    s.setPath(path, path != null && path.thickness() < s.getThickness(), now);
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
    private void updateEnvironment(Vec3 listener, int slow) {
        try {
            ListenerEnvironment env = AudioDistancePlugin.ENVIRONMENT;
            if (listener == null) {
                env.reset();
                return;
            }
            env.update(access.isUnderWater(listener), access.weatherAt(listener));
            env.setPosition(listener.x, listener.y, listener.z);
            Double zoneEcho = AudioDistancePlugin.LINK.zoneEcho();
            if (zoneEcho != null) {
                // The server's zone sets the echo (a cathedral, a padded room): no need to measure
                ticks++;
                env.updateRoom(RoomEstimate.forced(zoneEcho));
            } else if (ticks++ % (ROOM_INTERVAL_TICKS * slow) == 0) {
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

    /** Block lookups for this tick's path searches, shared so each block is read once. */
    private SoundPath.Grid cachedGrid() {
        java.util.Map<Long, Boolean> cache = new java.util.HashMap<>();
        return (x, y, z) -> cache.computeIfAbsent(((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFFL),
                k -> access.isOpenForSound(x, y, z));
    }

    private void reset() {
        AudioDistancePlugin.SPEAKERS.clear();
        AudioDistancePlugin.NEARBY.clear();
        AudioDistancePlugin.ENVIRONMENT.reset();
        access.reset();
    }
}
