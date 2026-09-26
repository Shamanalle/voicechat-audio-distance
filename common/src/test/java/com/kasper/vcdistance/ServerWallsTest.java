package com.kasper.vcdistance;

import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives {@link ServerWalls} through fake Simple Voice Chat objects. The fake "Opus" codec stores
 * raw PCM, so the test can see exactly what the listener would hear.
 */
public class ServerWallsTest {

    private static final int FRAME = 960;

    @TempDir
    Path dir;

    private ServerSettings settings;
    private ServerWalls walls;
    private final List<EntitySoundPacket> sent = new ArrayList<>();
    private final UUID channel = UUID.randomUUID();
    private final UUID speaker = UUID.randomUUID();
    private final UUID listener = UUID.randomUUID();
    private final Object listenerPlayer = new Object();
    private final Object listenerLevel = new Object();
    private Consumer<EntitySoundPacket> onSend = p -> { };
    private boolean failEncoding;
    private int encodersCreated;

    @BeforeEach
    void setUp() {
        settings = new ServerSettings(dir.resolve("server.properties"));
        settings.load();
        walls = new ServerWalls(settings);
        walls.markWorldAvailable();
    }

    // ---- fake codec ---------------------------------------------------------

    private static byte[] pack(short[] pcm) {
        ByteBuffer b = ByteBuffer.allocate(pcm.length * 2);
        for (short s : pcm) {
            b.putShort(s);
        }
        return b.array();
    }

    private static short[] unpack(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data);
        short[] pcm = new short[data.length / 2];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = b.getShort();
        }
        return pcm;
    }

    private static short[] tone(int frameIndex) {
        short[] pcm = new short[FRAME];
        for (int i = 0; i < FRAME; i++) {
            long n = (long) frameIndex * FRAME + i;
            pcm[i] = (short) Math.round(8000 * Math.sin(2 * Math.PI * 3000 * n / 48000.0));
        }
        return pcm;
    }

    private static double rms(short[] pcm) {
        double sum = 0;
        for (short s : pcm) {
            sum += (double) s * s;
        }
        return Math.sqrt(sum / pcm.length);
    }

    // ---- fake SVC objects ---------------------------------------------------

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Map<String, java.util.function.Function<Object[], Object>> methods) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, m, args) -> {
            java.util.function.Function<Object[], Object> f = methods.get(m.getName());
            if (f == null) {
                throw new UnsupportedOperationException(m.getName());
            }
            return f.apply(args);
        });
    }

    private OpusEncoder encoder() {
        encodersCreated++;
        Map<String, java.util.function.Function<Object[], Object>> m = new HashMap<>();
        m.put("encode", a -> {
            if (failEncoding) {
                throw new IllegalStateException("boom");
            }
            return pack((short[]) a[0]);
        });
        m.put("close", a -> null);
        m.put("resetState", a -> null);
        return proxy(OpusEncoder.class, m);
    }

    private OpusDecoder decoder() {
        Map<String, java.util.function.Function<Object[], Object>> m = new HashMap<>();
        m.put("decode", a -> unpack((byte[]) a[0]));
        m.put("close", a -> null);
        m.put("resetState", a -> null);
        return proxy(OpusDecoder.class, m);
    }

    /** Builder that starts as a copy of the original packet, like Simple Voice Chat's. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private EntitySoundPacket.Builder builder(UUID ch, long seq, byte[] data, boolean whisper, float distance) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("channelId", ch);
        fields.put("sequenceNumber", seq);
        fields.put("opusEncodedData", data);
        fields.put("whispering", whisper);
        fields.put("distance", distance);
        Object[] self = new Object[1];
        Map<String, java.util.function.Function<Object[], Object>> m = new HashMap<>();
        for (String name : new String[]{"channelId", "sender", "opusEncodedData", "sequenceNumber", "category",
                "entityUuid", "whispering", "distance"}) {
            m.put(name, a -> {
                fields.put(name, a[0]);
                return self[0];
            });
        }
        m.put("build", a -> packet((UUID) fields.get("channelId"), (long) fields.get("sequenceNumber"),
                (byte[]) fields.get("opusEncodedData"), (boolean) fields.get("whispering"), (float) fields.get("distance")));
        self[0] = proxy(EntitySoundPacket.Builder.class, m);
        return (EntitySoundPacket.Builder) self[0];
    }

    private EntitySoundPacket packet(UUID ch, long seq, byte[] data, boolean whisper, float distance) {
        Map<String, java.util.function.Function<Object[], Object>> m = new HashMap<>();
        m.put("getChannelId", a -> ch);
        m.put("getSender", a -> speaker);
        m.put("getOpusEncodedData", a -> data);
        m.put("getSequenceNumber", a -> seq);
        m.put("getCategory", a -> null);
        m.put("getEntityUuid", a -> speaker);
        m.put("isWhispering", a -> whisper);
        m.put("getDistance", a -> distance);
        m.put("entitySoundPacketBuilder", a -> builder(ch, seq, data, whisper, distance));
        return proxy(EntitySoundPacket.class, m);
    }

    private VoicechatServerApi api() {
        Map<String, java.util.function.Function<Object[], Object>> m = new HashMap<>();
        m.put("createEncoder", a -> encoder());
        m.put("createDecoder", a -> decoder());
        m.put("sendEntitySoundPacketTo", a -> {
            EntitySoundPacket p = (EntitySoundPacket) a[1];
            sent.add(p);
            onSend.accept(p);
            return null;
        });
        return proxy(VoicechatServerApi.class, m);
    }

    private VoicechatConnection connection(UUID player) {
        Map<String, java.util.function.Function<Object[], Object>> pm = new HashMap<>();
        pm.put("getUuid", a -> player);
        pm.put("getPlayer", a -> listenerPlayer);
        Map<String, java.util.function.Function<Object[], Object>> lm = new HashMap<>();
        lm.put("getServerLevel", a -> listenerLevel);
        de.maxhenkel.voicechat.api.ServerLevel level = proxy(de.maxhenkel.voicechat.api.ServerLevel.class, lm);
        pm.put("getServerLevel", a -> level);
        ServerPlayer sp = proxy(ServerPlayer.class, pm);
        Map<String, java.util.function.Function<Object[], Object>> m = new HashMap<>();
        m.put("getPlayer", a -> sp);
        return proxy(VoicechatConnection.class, m);
    }

    /** @return {cancelled, packet} */
    private boolean fire(EntitySoundPacket packet, UUID to, String source) {
        boolean[] cancelled = {false};
        VoicechatServerApi api = api();
        VoicechatConnection receiver = connection(to);
        Map<String, java.util.function.Function<Object[], Object>> m = new HashMap<>();
        m.put("getPacket", a -> packet);
        m.put("getSource", a -> source);
        m.put("getReceiverConnection", a -> receiver);
        m.put("getSenderConnection", a -> null);
        m.put("getVoicechat", a -> api);
        m.put("cancel", a -> {
            cancelled[0] = true;
            return true;
        });
        m.put("isCancelled", a -> cancelled[0]);
        m.put("isCancellable", a -> true);
        walls.onEntitySound(proxy(EntitySoundPacketEvent.class, m));
        return cancelled[0];
    }

    private boolean fireFrame(int i) {
        return fire(packet(channel, i, pack(tone(i)), false, 48F), listener, SoundPacketEvent.SOURCE_PROXIMITY);
    }

    private void setThickness(double thickness) {
        try {
            Thread.sleep(120); // walls are re-measured at most every 100 ms per pair
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        walls.tick((player, level, entity, x, y, z) -> thickness);
    }

    // ---- tests --------------------------------------------------------------

    @Test
    @DisplayName("Before the wall is measured, packets pass through untouched")
    void unknownThicknessPassesThrough() {
        assertFalse(fireFrame(0));
        assertTrue(sent.isEmpty());
    }

    @Test
    @DisplayName("Behind a wall the listener gets a muffled copy with the same metadata")
    void muffledBehindWall() {
        fireFrame(0);
        setThickness(2.0);
        double inRms = 0;
        double outRms = 0;
        for (int i = 1; i <= 30; i++) {
            assertTrue(fireFrame(i), "original packet must be replaced");
            EntitySoundPacket out = sent.get(sent.size() - 1);
            assertEquals(channel, out.getChannelId());
            assertEquals(i, out.getSequenceNumber());
            assertEquals(speaker, out.getEntityUuid());
            assertEquals(48F, out.getDistance());
            if (i > 10) {
                inRms += rms(tone(i));
                outRms += rms(unpack(out.getOpusEncodedData()));
            }
        }
        assertTrue(outRms < inRms * 0.1, "3 kHz behind two walls should be far quieter: " + outRms / inRms);
        assertEquals(1, walls.activeStreams());
    }

    @Test
    @DisplayName("Listeners with the addon are never processed by the server")
    void addonListenersAreSkipped() {
        walls.markAddonListener(listener);
        fireFrame(0);
        setThickness(3.0);
        assertFalse(fireFrame(1));
        assertTrue(sent.isEmpty());
        assertTrue(walls.hasAddon(listener));
        // Reconnecting voice chat keeps the addon marker; leaving the game drops it
        walls.releaseListener(listener);
        assertTrue(walls.hasAddon(listener));
        walls.forgetPlayer(listener);
        assertFalse(walls.hasAddon(listener));
    }

    @Test
    @DisplayName("Group, spectator and plugin audio pass through")
    void onlyProximityIsProcessed() {
        fireFrame(0);
        setThickness(3.0);
        for (String source : new String[]{SoundPacketEvent.SOURCE_GROUP, SoundPacketEvent.SOURCE_SPECTATOR, SoundPacketEvent.SOURCE_PLUGIN}) {
            assertFalse(fire(packet(channel, 5, pack(tone(5)), false, 48F), listener, source), source);
        }
        assertTrue(sent.isEmpty());
    }

    @Test
    @DisplayName("Our own re-sent packet is not processed again (no loop)")
    void noRecursion() {
        fireFrame(0);
        setThickness(3.0);
        int[] nested = {0};
        onSend = p -> {
            nested[0]++;
            // Simple Voice Chat dispatches plugin sends through the same event
            assertFalse(fire(p, listener, SoundPacketEvent.SOURCE_PROXIMITY));
        };
        assertTrue(fireFrame(1));
        assertEquals(1, nested[0]);
        assertEquals(1, sent.size());
    }

    @Test
    @DisplayName("The stream cap is respected")
    void streamCap() {
        settings.setMaxStreams(1);
        UUID other = UUID.randomUUID();
        fireFrame(0);
        fire(packet(channel, 0, pack(tone(0)), false, 48F), other, SoundPacketEvent.SOURCE_PROXIMITY);
        setThickness(3.0);
        assertTrue(fireFrame(1));
        assertFalse(fire(packet(channel, 1, pack(tone(1)), false, 48F), other, SoundPacketEvent.SOURCE_PROXIMITY),
                "second listener exceeds the cap and passes through");
        assertEquals(1, walls.activeStreams());
    }

    @Test
    @DisplayName("Encoder failures fall back to the original packet")
    void failureFallsBack() {
        fireFrame(0);
        setThickness(3.0);
        failEncoding = true;
        assertFalse(fireFrame(1));
        assertTrue(sent.isEmpty());
        assertEquals(0, walls.activeStreams());
    }

    @Test
    @DisplayName("Disabled server walls or disabled profile walls do nothing")
    void switches() {
        fireFrame(0);
        setThickness(3.0);
        settings.setServerWalls(false);
        assertFalse(fireFrame(1));
        settings.setServerWalls(true);
        settings.profile().setOcclusionEnabled(false);
        assertFalse(fireFrame(2));
        assertTrue(sent.isEmpty());
    }

    @Test
    @DisplayName("When the wall disappears the stream returns to pass-through and frees its encoder")
    void returnsToPassThrough() {
        fireFrame(0);
        setThickness(2.0);
        for (int i = 1; i < 10; i++) {
            fireFrame(i);
        }
        assertEquals(1, walls.activeStreams());
        fireFrame(10); // keep the pair active
        setThickness(0.0);
        int i = 11;
        while (walls.activeStreams() > 0 && i < 200) {
            fireFrame(i++);
        }
        assertEquals(0, walls.activeStreams(), "encoder should be released once the filter is open again");
        int before = sent.size();
        assertFalse(fireFrame(i));
        assertEquals(before, sent.size());
    }

    @Test
    @DisplayName("One encoder per listener, not per frame")
    void encoderReused() {
        fireFrame(0);
        setThickness(2.0);
        for (int i = 1; i < 20; i++) {
            fireFrame(i);
        }
        assertEquals(1, encodersCreated);
        walls.clear();
        assertEquals(0, walls.activeStreams());
    }

    @Test
    @DisplayName("Group voices: untouched by default, cancelled by the group rules the admin turned on")
    void groupRules() {
        ServerPlayers players = new ServerPlayers();
        walls = new ServerWalls(settings, players);
        players.update(new ServerPlayers.Info(speaker, "Dead", "world", 0, 64, 0, false, false, false, "", "", List.of(), ""));
        players.update(new ServerPlayers.Info(listener, "Friend", "world", 500, 64, 0, false, true, false, "", "", List.of(), ""));
        EntitySoundPacket p = packet(channel, 0, pack(tone(0)), false, 48F);

        settings.setDeadSilent(true);
        assertFalse(fire(p, listener, SoundPacketEvent.SOURCE_GROUP), "dead_players_silent alone leaves groups alone");
        settings.setGroupDeadSilent(true);
        assertTrue(fire(p, listener, SoundPacketEvent.SOURCE_GROUP));
        assertTrue(sent.isEmpty());

        // Proximity packets from far away are Simple Voice Chat's business; group rules only touch group audio
        settings.setDeadSilent(false);
        assertFalse(fire(p, listener, SoundPacketEvent.SOURCE_SPECTATOR));
    }
}
