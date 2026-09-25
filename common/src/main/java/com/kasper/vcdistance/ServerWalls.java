package com.kasper.vcdistance;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;
import de.maxhenkel.voicechat.api.packets.LocationalSoundPacket;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side wall muffling for players who do not have the addon.
 * <p>
 * Simple Voice Chat sends every proximity voice packet to each listener separately and fires a
 * {@link SoundPacketEvent} for each of them. When a wall stands between the speaker and a listener
 * without the addon, the packet is decoded once per speaker frame, run through that listener's
 * {@link VoiceFilter}, re-encoded with that listener's encoder and sent in place of the original.
 * Everything else passes through untouched:
 * <ul>
 *     <li>listeners who have the addon (they muffle locally, with better quality and no server cost),</li>
 *     <li>listeners with a clear line of sight,</li>
 *     <li>group, spectator and plugin audio,</li>
 *     <li>anything above {@link ServerSettings#getMaxStreams()} voices at once.</li>
 * </ul>
 * Any failure falls back to the original packet, so a problem here can never silence voice chat.
 * <p>
 * Threading: packet events arrive on Simple Voice Chat's server thread; {@link #tick} runs on the
 * Minecraft server thread and is the only place that touches the world (through
 * {@link ThicknessProvider}).
 */
public final class ServerWalls {

    /** Resolves the acoustic thickness between a listener and a sound source, on the server thread. */
    @FunctionalInterface
    public interface ThicknessProvider {
        /**
         * @param listener      the listener's Minecraft player object ({@code ServerPlayer})
         * @param level         the listener's Minecraft level ({@code ServerLevel}), as reported by Simple Voice Chat
         * @param speakerEntity the speaking entity, or {@code null} for a fixed position
         * @return thickness in stone blocks, or {@code NaN} when it cannot be determined
         */
        double thickness(Object listener, Object level, UUID speakerEntity, double x, double y, double z);
    }

    private static final long ACTIVE_NANOS = TimeUnit.MILLISECONDS.toNanos(800);
    private static final long FORGET_NANOS = TimeUnit.SECONDS.toNanos(4);
    private static final long PRUNE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(2);
    private static final long TRACE_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
    private static final int MAX_TRACES_PER_TICK = 48;

    private record PairKey(UUID channel, UUID listener) {
    }

    static final class Pair {
        final UUID listener;
        final VoiceFilter filter = new VoiceFilter();
        volatile Object listenerPlayer;
        volatile Object listenerLevel;
        volatile UUID speakerEntity;
        volatile double x;
        volatile double y;
        volatile double z;
        volatile long lastSeenNanos;
        volatile long lastTraceNanos;
        volatile double thickness = Double.NaN;
        OpusEncoder encoder;

        Pair(UUID listener) {
            this.listener = listener;
        }
    }

    private static final class Stream {
        OpusDecoder decoder;
        long lastSequence = Long.MIN_VALUE;
        short[] pcm;
        long lastSeenNanos;
    }

    private final ServerSettings settings;
    private final Set<UUID> addonListeners = ConcurrentHashMap.newKeySet();
    private final Map<PairKey, Pair> pairs = new ConcurrentHashMap<>();
    private final Map<UUID, Stream> streams = new ConcurrentHashMap<>();
    private final AtomicInteger encoding = new AtomicInteger();
    private final ThreadLocal<Boolean> resending = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private volatile boolean worldAvailable;
    private volatile long lastPruneNanos = System.nanoTime();
    private volatile boolean loggedFailure;

    public ServerWalls(ServerSettings settings) {
        this.settings = settings;
    }

    // -------------------------------------------------------------------------
    // State from the rest of the addon
    // -------------------------------------------------------------------------

    /** Called once a server tick feeds wall data; without it nothing is processed. */
    public void markWorldAvailable() {
        worldAvailable = true;
    }

    /** A listener announced the addon: it muffles locally, so the server leaves its audio alone. */
    public void markAddonListener(UUID player) {
        addonListeners.add(player);
    }

    public boolean hasAddon(UUID player) {
        return addonListeners.contains(player);
    }

    public void forgetPlayer(UUID player) {
        addonListeners.remove(player);
        for (Pair p : pairs.values()) {
            if (p.listener.equals(player)) {
                p.lastSeenNanos = 0L;
            }
        }
    }

    /** Number of voices currently re-encoded. */
    public int activeStreams() {
        return encoding.get();
    }

    // -------------------------------------------------------------------------
    // Packet events (Simple Voice Chat server thread)
    // -------------------------------------------------------------------------

    // Simple Voice Chat's builders start as a copy of the original packet (channel, sender,
    // sequence number, category, entity, whisper flag, distance, position), so only the audio is
    // replaced. Only opusEncodedData(...) and build() are called: the published 2.6.0 API has no
    // setters for the other fields.

    public void onEntitySound(EntitySoundPacketEvent event) {
        EntitySoundPacket p = event.getPacket();
        if (p == null) {
            return;
        }
        byte[] processed = process(event, p.getChannelId(), p.getSequenceNumber(), p.getOpusEncodedData(),
                p.getEntityUuid(), null);
        if (processed == null) {
            return;
        }
        resend(event, () -> event.getVoicechat().sendEntitySoundPacketTo(event.getReceiverConnection(),
                p.entitySoundPacketBuilder()
                        .opusEncodedData(processed)
                        .build()));
    }

    public void onLocationalSound(LocationalSoundPacketEvent event) {
        LocationalSoundPacket p = event.getPacket();
        if (p == null || p.getPosition() == null) {
            return;
        }
        byte[] processed = process(event, p.getChannelId(), p.getSequenceNumber(), p.getOpusEncodedData(),
                null, p.getPosition());
        if (processed == null) {
            return;
        }
        resend(event, () -> event.getVoicechat().sendLocationalSoundPacketTo(event.getReceiverConnection(),
                p.locationalSoundPacketBuilder()
                        .opusEncodedData(processed)
                        .build()));
    }

    /**
     * @return the re-encoded frame, or {@code null} to let the original packet through
     */
    private byte[] process(SoundPacketEvent<?> event, UUID channel, long sequence, byte[] opus,
                           UUID speakerEntity, Position position) {
        if (resending.get() || !SoundPacketEvent.SOURCE_PROXIMITY.equals(event.getSource())) {
            return null;
        }
        if (!worldAvailable || !settings.isServerWalls() || channel == null || opus == null || opus.length == 0) {
            return null;
        }
        DistanceConfig profile = settings.profile();
        if (!profile.isOcclusionEnabled()) {
            return null;
        }
        VoicechatConnection receiver = event.getReceiverConnection();
        if (receiver == null || receiver.getPlayer() == null) {
            return null;
        }
        UUID listener = receiver.getPlayer().getUuid();
        if (listener == null || addonListeners.contains(listener)) {
            return null;
        }

        long now = System.nanoTime();
        maybePrune(now);

        Pair pair = pairs.computeIfAbsent(new PairKey(channel, listener), k -> new Pair(listener));
        pair.lastSeenNanos = now;
        pair.listenerPlayer = receiver.getPlayer().getPlayer();
        pair.listenerLevel = receiver.getPlayer().getServerLevel() != null
                ? receiver.getPlayer().getServerLevel().getServerLevel()
                : null;
        pair.speakerEntity = speakerEntity;
        if (position != null) {
            pair.x = position.getX();
            pair.y = position.getY();
            pair.z = position.getZ();
        }

        synchronized (pair) {
            double thickness = pair.thickness;
            double strength = profile.getOcclusionStrength();
            double muffle = Double.isNaN(thickness) ? 0.0 : OcclusionModel.muffle(thickness, strength);
            double loss = Double.isNaN(thickness) ? 0.0 : OcclusionModel.lossDb(thickness, strength);
            boolean wanted = muffle > 0.002 || loss > 0.05;

            if (pair.encoder == null) {
                if (!wanted || encoding.get() >= settings.getMaxStreams()) {
                    return null;
                }
                pair.encoder = event.getVoicechat().createEncoder();
                encoding.incrementAndGet();
            }

            try {
                short[] pcm = decode(event.getVoicechat(), channel, sequence, opus, now);
                if (pcm == null) {
                    stop(pair);
                    return null;
                }
                short[] frame = pair.filter.process(pcm.clone(), muffle, loss);
                byte[] encoded = pair.encoder.encode(frame);
                if (!pair.filter.isEngaged()) {
                    // The filter has glided back open: this frame is the crossfade to dry, then pass through again
                    stop(pair);
                }
                return encoded;
            } catch (Throwable t) {
                logFailure(t);
                stop(pair);
                return null;
            }
        }
    }

    private short[] decode(VoicechatServerApi api, UUID channel, long sequence, byte[] opus, long now) {
        Stream stream = streams.computeIfAbsent(channel, c -> new Stream());
        synchronized (stream) {
            stream.lastSeenNanos = now;
            if (stream.pcm != null && stream.lastSequence == sequence) {
                return stream.pcm;
            }
            if (stream.decoder == null) {
                stream.decoder = api.createDecoder();
            } else if (stream.lastSequence != sequence - 1) {
                // Frames were skipped while nobody needed this stream: start clean
                stream.decoder.resetState();
            }
            stream.pcm = stream.decoder.decode(opus);
            stream.lastSequence = sequence;
            return stream.pcm;
        }
    }

    private void resend(SoundPacketEvent<?> event, Runnable send) {
        resending.set(Boolean.TRUE);
        try {
            send.run();
        } catch (Throwable t) {
            // The original packet was not cancelled yet and still goes out
            logFailure(t);
            return;
        } finally {
            resending.set(Boolean.FALSE);
        }
        event.cancel();
    }

    private void stop(Pair pair) {
        if (pair.encoder != null) {
            try {
                pair.encoder.close();
            } catch (Throwable ignored) {
            }
            pair.encoder = null;
            encoding.decrementAndGet();
        }
    }

    // -------------------------------------------------------------------------
    // Server tick (Minecraft server thread)
    // -------------------------------------------------------------------------

    /** Measures walls for every active listener/speaker pair. */
    public void tick(ThicknessProvider provider) {
        worldAvailable = true;
        if (!settings.isServerWalls() || !settings.profile().isOcclusionEnabled()) {
            return;
        }
        long now = System.nanoTime();
        int budget = MAX_TRACES_PER_TICK;
        for (Pair pair : pairs.values()) {
            if (budget <= 0) {
                break;
            }
            if (now - pair.lastSeenNanos > ACTIVE_NANOS || now - pair.lastTraceNanos < TRACE_INTERVAL_NANOS) {
                continue;
            }
            Object listener = pair.listenerPlayer;
            Object level = pair.listenerLevel;
            if (listener == null || level == null) {
                continue;
            }
            try {
                pair.thickness = provider.thickness(listener, level, pair.speakerEntity, pair.x, pair.y, pair.z);
            } catch (Throwable t) {
                pair.thickness = Double.NaN;
                logFailure(t);
            }
            pair.lastTraceNanos = now;
            budget--;
        }
    }

    // -------------------------------------------------------------------------
    // Housekeeping
    // -------------------------------------------------------------------------

    private void maybePrune(long now) {
        if (now - lastPruneNanos < PRUNE_INTERVAL_NANOS) {
            return;
        }
        lastPruneNanos = now;
        pairs.entrySet().removeIf(e -> {
            Pair p = e.getValue();
            if (now - p.lastSeenNanos <= FORGET_NANOS) {
                return false;
            }
            synchronized (p) {
                stop(p);
            }
            return true;
        });
        streams.entrySet().removeIf(e -> {
            Stream s = e.getValue();
            if (now - s.lastSeenNanos <= FORGET_NANOS) {
                return false;
            }
            synchronized (s) {
                if (s.decoder != null) {
                    try {
                        s.decoder.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
            return true;
        });
    }

    /** Releases every encoder and decoder, e.g. when the voice chat server stops. */
    public void clear() {
        for (Pair p : pairs.values()) {
            synchronized (p) {
                stop(p);
            }
        }
        pairs.clear();
        for (Stream s : streams.values()) {
            synchronized (s) {
                if (s.decoder != null) {
                    try {
                        s.decoder.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        streams.clear();
        addonListeners.clear();
    }

    private void logFailure(Throwable t) {
        if (!loggedFailure) {
            loggedFailure = true;
            DistanceConfig.LOGGER.warn("Server-side wall muffling failed, passing voices through unchanged: {}", t.toString());
        } else {
            DistanceConfig.LOGGER.debug("Server-side wall muffling failed: {}", t.toString());
        }
    }
}
