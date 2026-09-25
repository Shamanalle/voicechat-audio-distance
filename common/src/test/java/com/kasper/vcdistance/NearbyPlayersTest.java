package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class NearbyPlayersTest {

    @Test
    @DisplayName("Nearby message round-trips states in order and skips junk")
    void nearbyRoundTrip() {
        Map<UUID, VoiceState> states = new LinkedHashMap<>();
        for (VoiceState v : VoiceState.values()) {
            states.put(UUID.randomUUID(), v);
        }
        Map<UUID, VoiceState> parsed = LinkProtocol.parseNearby(LinkProtocol.nearby(states));
        assertEquals(states, parsed);

        Map<UUID, VoiceState> junk = LinkProtocol.parseNearby("protocol=1\nplayer.nope=ok\nplayer." + UUID.randomUUID() + "=later\nother=1\n");
        assertNotNull(junk);
        assertTrue(junk.isEmpty());
        assertNull(LinkProtocol.parseNearby("player.x=ok\n"));
        assertNull(LinkProtocol.parseNearby(null));
    }

    @Test
    @DisplayName("Nearby message lists at most MAX_NEARBY players and fits the payload limit")
    void nearbyLimit() {
        Map<UUID, VoiceState> states = new LinkedHashMap<>();
        for (int i = 0; i < LinkProtocol.MAX_NEARBY * 2; i++) {
            states.put(UUID.randomUUID(), VoiceState.DISCONNECTED);
        }
        String text = LinkProtocol.nearby(states);
        assertTrue(text.length() < LinkProtocol.MAX_LENGTH);
        assertEquals(LinkProtocol.MAX_NEARBY, LinkProtocol.parseNearby(text).size());
    }

    @Test
    @DisplayName("Voice state follows Simple Voice Chat's connection flags")
    void voiceState() {
        assertEquals(VoiceState.NO_VOICE_CHAT, VoiceState.of(false, false, false, false));
        assertEquals(VoiceState.DISCONNECTED, VoiceState.of(true, false, false, false));
        assertEquals(VoiceState.SOUND_OFF, VoiceState.of(true, true, true, true));
        assertEquals(VoiceState.GROUP, VoiceState.of(true, true, false, true));
        assertEquals(VoiceState.CONNECTED, VoiceState.of(true, true, false, false));
        // Shown as connected by a voice bridge plugin
        assertEquals(VoiceState.CONNECTED, VoiceState.of(false, true, false, false));
        for (VoiceState v : VoiceState.values()) {
            assertSame(v, VoiceState.fromId(v.getId()));
        }
    }

    @Test
    @DisplayName("Client link keeps nearby states until they go stale or the server is left")
    void linkStates() {
        ServerLink link = new ServerLink();
        UUID id = UUID.randomUUID();
        long t = 1_000_000_000L;
        assertNull(link.voiceState(id, t));
        assertFalse(link.hasVoiceStates(t));

        link.onNearby(LinkProtocol.nearby(Map.of(id, VoiceState.SOUND_OFF)), t);
        assertEquals(VoiceState.SOUND_OFF, link.voiceState(id, t + 1));
        assertTrue(link.hasVoiceStates(t + 1));
        assertNull(link.voiceState(UUID.randomUUID(), t + 1));

        link.onNearby("garbage", t + 2);
        assertEquals(VoiceState.SOUND_OFF, link.voiceState(id, t + 2));

        assertNull(link.voiceState(id, t + ServerLink.NEARBY_STALE_NANOS + 1));
        assertFalse(link.hasVoiceStates(t + ServerLink.NEARBY_STALE_NANOS + 1));

        link.onNearby(LinkProtocol.nearby(Map.of(id, VoiceState.CONNECTED)), t);
        link.reset();
        assertNull(link.voiceState(id, t));
    }

    @Test
    @DisplayName("Monitor rows: talkers first, then silent players by distance, no duplicates")
    void rows() {
        SpeakerRegistry registry = new SpeakerRegistry();
        UUID alex = UUID.randomUUID();
        UUID steve = UUID.randomUUID();
        UUID notch = UUID.randomUUID();
        SpeakerRegistry.Speaker talking = registry.onEntityFrame(UUID.randomUUID(), steve, false, 48F, new short[960]);
        talking.setDistance(20);
        SpeakerRegistry.Speaker radio = registry.onLocationalFrame(UUID.randomUUID(), 0, 0, 0, 48F, new short[960]);
        radio.setDistance(30);

        NearbyPlayers nearby = new NearbyPlayers();
        nearby.update(List.of(
                new NearbyPlayers.Player(notch, "Notch", 31),
                new NearbyPlayers.Player(steve, "Steve", 20),
                new NearbyPlayers.Player(alex, "Alex", 5)));
        assertEquals("Alex", nearby.players().get(0).name());

        Map<UUID, VoiceState> states = Map.of(steve, VoiceState.SOUND_OFF, notch, VoiceState.NO_VOICE_CHAT);
        List<NearbyPlayers.Row> rows = NearbyPlayers.rows(registry.active(System.nanoTime()), nearby.players(), states::get);

        assertEquals(4, rows.size());
        assertSame(talking, rows.get(0).speaker());
        assertEquals(VoiceState.SOUND_OFF, rows.get(0).state());
        assertSame(radio, rows.get(1).speaker());
        assertNull(rows.get(1).playerId());
        assertNull(rows.get(1).state());
        assertFalse(rows.get(2).isTalking());
        assertEquals(alex, rows.get(2).playerId());
        assertNull(rows.get(2).state());
        assertEquals("Notch", rows.get(3).name());
        assertEquals(VoiceState.NO_VOICE_CHAT, rows.get(3).state());

        nearby.clear();
        assertTrue(nearby.players().isEmpty());
    }
}
