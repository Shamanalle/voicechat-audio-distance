package com.kasper.vcdistance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Live registry of the voice sources the client is currently hearing.
 * <p>
 * Audio threads register frames here; the client tick (main thread) resolves positions, runs the
 * occlusion raycast and writes the results back. Audio threads only ever read those results, so
 * the world is never touched off the main thread.
 */
public final class SpeakerRegistry {

    /** Sources that stopped sending audio longer ago than this are forgotten. */
    public static final long FORGET_AFTER_NANOS = TimeUnit.SECONDS.toNanos(4);
    /** Sources are considered "talking" for the UI and the tracer within this window. */
    public static final long ACTIVE_WINDOW_NANOS = TimeUnit.MILLISECONDS.toNanos(600);

    public enum Kind {
        ENTITY,
        LOCATIONAL
    }

    public static final class Speaker {
        private final UUID channelId;
        private final VoiceFilter filter = new VoiceFilter();

        volatile Kind kind;
        volatile UUID entityId;
        volatile boolean whispering;
        volatile double soundX;
        volatile double soundY;
        volatile double soundZ;
        volatile float maxDistance;
        volatile long lastHeardNanos;
        volatile float levelDb = -90.0F;

        // Written by the main thread
        private volatile double thickness;
        private volatile boolean occlusionKnown;
        private volatile double distance = -1.0;
        private volatile double bearing = Double.NaN;
        private volatile String displayName;
        private volatile long lastTraceNanos;
        private volatile int cachedEntityNetworkId = Integer.MIN_VALUE;
        private volatile long lastEntitySearchNanos;

        Speaker(UUID channelId) {
            this.channelId = channelId;
        }

        public UUID getChannelId() {
            return channelId;
        }

        public Kind getKind() {
            return kind;
        }

        public UUID getEntityId() {
            return entityId;
        }

        public boolean isWhispering() {
            return whispering;
        }

        public double getSoundX() {
            return soundX;
        }

        public double getSoundY() {
            return soundY;
        }

        public double getSoundZ() {
            return soundZ;
        }

        /** Maximum hearing distance SVC reported for the last frame (whisper distance while whispering). */
        public float getMaxDistance() {
            return maxDistance;
        }

        public long getLastHeardNanos() {
            return lastHeardNanos;
        }

        /** RMS level of the last received frame in dBFS (before any processing). */
        public float getLevelDb() {
            return levelDb;
        }

        public VoiceFilter getFilter() {
            return filter;
        }

        public double getThickness() {
            return thickness;
        }

        public boolean isOcclusionKnown() {
            return occlusionKnown;
        }

        public void setOcclusion(double thickness, long nowNanos) {
            this.thickness = Math.max(0.0, thickness);
            this.occlusionKnown = true;
            this.lastTraceNanos = nowNanos;
        }

        public void clearOcclusion() {
            this.thickness = 0.0;
            this.occlusionKnown = false;
        }

        public long getLastTraceNanos() {
            return lastTraceNanos;
        }

        /** Distance to the listener in blocks, or a negative value when the source position is unknown. */
        public double getDistance() {
            return distance;
        }

        public void setDistance(double distance) {
            this.distance = distance;
        }

        /** Degrees from where the listener looks, positive to the right; NaN when unknown ({@link Bearing}). */
        public double getBearing() {
            return bearing;
        }

        public void setBearing(double bearing) {
            this.bearing = bearing;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public int getCachedEntityNetworkId() {
            return cachedEntityNetworkId;
        }

        public void setCachedEntityNetworkId(int id) {
            this.cachedEntityNetworkId = id;
        }

        public long getLastEntitySearchNanos() {
            return lastEntitySearchNanos;
        }

        public void setLastEntitySearchNanos(long nanos) {
            this.lastEntitySearchNanos = nanos;
        }

        public boolean isActive(long nowNanos) {
            return nowNanos - lastHeardNanos <= ACTIVE_WINDOW_NANOS;
        }
    }

    private static final long PRUNE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(2);

    private final Map<UUID, Speaker> speakers = new ConcurrentHashMap<>();
    private volatile long lastPruneNanos = System.nanoTime();

    public Speaker onEntityFrame(UUID channelId, UUID entityId, boolean whispering, float maxDistance, short[] pcm) {
        Speaker s = speakers.computeIfAbsent(channelId, Speaker::new);
        s.kind = Kind.ENTITY;
        s.entityId = entityId;
        s.whispering = whispering;
        s.maxDistance = maxDistance;
        touch(s, pcm);
        return s;
    }

    public Speaker onLocationalFrame(UUID channelId, double x, double y, double z, float maxDistance, short[] pcm) {
        Speaker s = speakers.computeIfAbsent(channelId, Speaker::new);
        s.kind = Kind.LOCATIONAL;
        s.entityId = null;
        s.whispering = false;
        s.soundX = x;
        s.soundY = y;
        s.soundZ = z;
        s.maxDistance = maxDistance;
        touch(s, pcm);
        return s;
    }

    public Speaker get(UUID channelId) {
        return channelId == null ? null : speakers.get(channelId);
    }

    /** Speakers heard within {@link #ACTIVE_WINDOW_NANOS}, closest first. */
    public List<Speaker> active(long nowNanos) {
        List<Speaker> list = new ArrayList<>();
        for (Speaker s : speakers.values()) {
            if (s.isActive(nowNanos)) {
                list.add(s);
            }
        }
        list.sort(Comparator.comparingDouble(s -> s.distance < 0 ? Double.MAX_VALUE : s.distance));
        return list;
    }

    public void prune(long nowNanos) {
        speakers.values().removeIf(s -> nowNanos - s.lastHeardNanos > FORGET_AFTER_NANOS);
    }

    public int size() {
        return speakers.size();
    }

    public void clear() {
        speakers.clear();
    }

    private void touch(Speaker s, short[] pcm) {
        long now = System.nanoTime();
        s.lastHeardNanos = now;
        s.levelDb = rmsDb(pcm);
        // Builds without a client tick (Forge/NeoForge lite) rely on this to forget old speakers
        if (now - lastPruneNanos > PRUNE_INTERVAL_NANOS) {
            lastPruneNanos = now;
            prune(now);
        }
    }

    static float rmsDb(short[] pcm) {
        if (pcm == null || pcm.length == 0) {
            return -90.0F;
        }
        double sum = 0.0;
        for (short v : pcm) {
            sum += (double) v * v;
        }
        double rms = Math.sqrt(sum / pcm.length) / 32768.0;
        if (rms <= 1e-5) {
            return -90.0F;
        }
        return (float) Math.max(-90.0, 20.0 * Math.log10(rms));
    }
}
