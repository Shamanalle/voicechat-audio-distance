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

    /** Time constant of the glide when a voice's direction changes (wall to doorway and back). */
    static final double DIRECTION_GLIDE_SECONDS = 0.15;

    public enum Kind {
        ENTITY,
        LOCATIONAL
    }

    public static final class Speaker {
        private final UUID channelId;
        private final VoiceFilter filter = new VoiceFilter();
        private final Reverb reverb = new Reverb();

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
        private volatile boolean underWater;
        private volatile EnvironmentEffects.Weather weather = EnvironmentEffects.Weather.CLEAR;
        // The way around a wall (client tick), or no opening
        private volatile boolean hasOpening;
        private volatile double openingX;
        private volatile double openingY;
        private volatile double openingZ;
        private volatile double pathThickness = Double.NaN;
        private volatile double pathLength = Double.NaN;
        private volatile long lastPathNanos = Long.MIN_VALUE;
        // The mix of the straight way and the way round (audio thread), and the direction the voice is heard from
        private volatile SoundBlend blend;
        private double[] heard;
        private long heardNanos;
        // The space around the speaker (client tick); null: the listener's own
        private volatile RoomEstimate room;
        private volatile long lastRoomNanos = Long.MIN_VALUE;
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

        public Reverb getReverb() {
            return reverb;
        }

        /** The speaker's head is under water (client tick). */
        public boolean isUnderWater() {
            return underWater;
        }

        /** Weather over the speaker (client tick). */
        public EnvironmentEffects.Weather getWeather() {
            return weather;
        }

        /** {@code true} while there is a way round the wall, from {@link #getOpeningX()} etc. */
        public boolean hasOpening() {
            return hasOpening;
        }

        public double getOpeningX() {
            return openingX;
        }

        public double getOpeningY() {
            return openingY;
        }

        public double getOpeningZ() {
            return openingZ;
        }

        /** Muffling of the way round, in stone blocks, or NaN when there is none. */
        public double getPathThickness() {
            return pathThickness;
        }

        /** Length of the way round, in blocks, or NaN when there is none. */
        public double getPathLength() {
            return pathLength;
        }

        public long getLastPathNanos() {
            return lastPathNanos;
        }

        /** Records the way round a wall ({@code path} null: none). */
        public void setPath(SoundPath.Result path, long nowNanos) {
            lastPathNanos = nowNanos;
            if (path == null || path.corners() == 0) {
                clearPath();
                return;
            }
            openingX = path.openingX();
            openingY = path.openingY();
            openingZ = path.openingZ();
            pathThickness = path.thickness();
            pathLength = path.length();
            hasOpening = true;
        }

        public void clearPath() {
            hasOpening = false;
            pathThickness = Double.NaN;
            pathLength = Double.NaN;
        }

        /** The mix of the straight way and the way round last applied to the voice, or null. */
        public SoundBlend getBlend() {
            return blend;
        }

        public void setBlend(SoundBlend blend) {
            this.blend = blend;
        }

        /** {@code true} when most of the voice comes round a wall rather than through it. */
        public boolean isHeardRound() {
            SoundBlend b = blend;
            return hasOpening && b != null && b.mostlyRound();
        }

        /**
         * Glides the direction the voice is heard from towards {@code target} (unit vectors), so a
         * voice moving from a wall to a doorway pans instead of jumping.
         *
         * @param direct straight towards the speaker
         * @param target where it should be heard from now
         * @return the direction to use, or {@code null} once it has settled back on the straight line
         */
        public synchronized double[] glideDirection(double[] direct, double[] target, boolean round, long nowNanos) {
            if (heard == null) {
                if (!round) {
                    return null;
                }
                heard = direct.clone();
                heardNanos = nowNanos;
            }
            double dt = Math.max(0.0, Math.min(0.5, (nowNanos - heardNanos) / 1e9));
            heardNanos = nowNanos;
            double a = 1.0 - Math.exp(-dt / DIRECTION_GLIDE_SECONDS);
            double x = heard[0] + (target[0] - heard[0]) * a;
            double y = heard[1] + (target[1] - heard[1]) * a;
            double z = heard[2] + (target[2] - heard[2]) * a;
            double len = Math.sqrt(x * x + y * y + z * z);
            if (len < 1e-6) {
                heard = target.clone();
            } else {
                heard = new double[]{x / len, y / len, z / len};
            }
            if (!round && heard[0] * target[0] + heard[1] * target[1] + heard[2] * target[2] > 0.99996) {
                heard = null; // back on the straight line (within half a degree)
                return null;
            }
            return heard.clone();
        }

        /** The space the speaker is in, or {@code null} when it is the listener's own (or not known yet). */
        public RoomEstimate getRoom() {
            return room;
        }

        public long getLastRoomNanos() {
            return lastRoomNanos;
        }

        public void setRoom(RoomEstimate room, long nowNanos) {
            this.room = room;
            this.lastRoomNanos = nowNanos;
        }

        public void setSurroundings(boolean underWater, EnvironmentEffects.Weather weather) {
            this.underWater = underWater;
            this.weather = weather == null ? EnvironmentEffects.Weather.CLEAR : weather;
        }

        /** Wall thickness on the straight line, in stone blocks. */
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
