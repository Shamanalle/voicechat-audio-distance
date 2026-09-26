package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class HudLogicTest {

    @Test
    @DisplayName("Bearing follows Minecraft's yaw: ahead, right, behind, left")
    void bearing() {
        // Yaw 0 looks towards +Z
        assertEquals(0.0, Bearing.relative(0, 10, 0), 1e-9);
        assertEquals(90.0, Bearing.relative(-10, 0, 0), 1e-9);   // -X is to the right when facing +Z
        assertEquals(-90.0, Bearing.relative(10, 0, 0), 1e-9);
        assertEquals(180.0, Math.abs(Bearing.relative(0, -10, 0)), 1e-9);
        // Facing -X (yaw 90), a source at -X is straight ahead
        assertEquals(0.0, Bearing.relative(-10, 0, 90), 1e-9);
        assertEquals(0.0, Bearing.relative(-10, 0, 450), 1e-9);

        assertEquals("↑", Bearing.arrow(10));
        assertEquals("→", Bearing.arrow(95));
        assertEquals("↓", Bearing.arrow(-179));
        assertEquals("↖", Bearing.arrow(-40));
        assertEquals("", Bearing.arrow(Double.NaN));
        assertEquals(-170.0, Bearing.wrap(190), 1e-9);
    }

    @Test
    @DisplayName("Hearing estimate counts players in range by their voice chat state")
    void hearing() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        List<NearbyPlayers.Player> players = List.of(
                new NearbyPlayers.Player(a, "A", 5),
                new NearbyPlayers.Player(b, "B", 10),
                new NearbyPlayers.Player(c, "C", 20),
                new NearbyPlayers.Player(far, "Far", 40));
        Map<UUID, VoiceState> states = Map.of(a, VoiceState.CONNECTED, b, VoiceState.SOUND_OFF, far, VoiceState.CONNECTED);

        HearingEstimate e = HearingEstimate.of(players, 24, states::get);
        assertEquals(new HearingEstimate(3, 1, 1, 1), e);
        assertFalse(e.isExact());

        // Whispering: only the closest two are in range
        HearingEstimate w = HearingEstimate.of(players, 12, states::get);
        assertEquals(new HearingEstimate(2, 1, 1, 0), w);
        assertTrue(w.isExact());
    }

    @Test
    @DisplayName("Hearing estimate follows Simple Voice Chat groups: members anywhere, nearby only for open groups")
    void hearingInGroups() {
        UUID mate = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        UUID isolated = UUID.randomUUID();
        List<NearbyPlayers.Player> players = List.of(
                new NearbyPlayers.Player(mate, "Mate", 5),
                new NearbyPlayers.Player(stranger, "Stranger", 8),
                new NearbyPlayers.Player(isolated, "Isolated", 10));
        Map<UUID, VoiceState> states = Map.of(mate, VoiceState.GROUP, stranger, VoiceState.CONNECTED, isolated, VoiceState.GROUP);
        // Two more members far away hear you, one has the sound off; the nearby mate is one of the two
        LinkProtocol.GroupInfo group = new LinkProtocol.GroupInfo(Set.of(mate), Set.of(isolated), 2, 1);

        // Not in a group: the player in someone else's isolated group does not hear you
        assertEquals(new HearingEstimate(3, 2, 1, 0),
                HearingEstimate.of(players, 24, states::get, null, new LinkProtocol.GroupInfo(Set.of(), Set.of(isolated), -1, -1)));
        // Normal group: nearby players outside it do not hear you; the group does, wherever it is
        assertEquals(new HearingEstimate(3, 2, 1, 0, 3), HearingEstimate.of(players, 24, states::get, "normal", group));
        assertEquals(new HearingEstimate(3, 2, 1, 0, 3), HearingEstimate.of(players, 24, states::get, "isolated", group));
        // Open group: nearby players too, the nearby mate counted once
        assertEquals(new HearingEstimate(5, 3, 2, 0, 3), HearingEstimate.of(players, 24, states::get, "open", group));
        // Server without group data: only nearby players
        assertEquals(new HearingEstimate(0, 0, 0, 0, 0), HearingEstimate.of(players, 24, states::get, "normal", LinkProtocol.GroupInfo.NONE));
        assertEquals(new HearingEstimate(3, 3, 0, 0, 0), HearingEstimate.of(players, 24, states::get, "open", LinkProtocol.GroupInfo.NONE));
    }

    @Test
    @DisplayName("The nearby message carries the group: mates, isolated players and totals; old messages have none")
    void nearbyGroupProtocol() {
        UUID mate = UUID.randomUUID();
        UUID isolated = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Map<UUID, VoiceState> states = new java.util.LinkedHashMap<>();
        states.put(mate, VoiceState.GROUP);
        states.put(isolated, VoiceState.GROUP);
        states.put(other, VoiceState.CONNECTED);
        String text = LinkProtocol.nearby(states, new LinkProtocol.GroupInfo(Set.of(mate), Set.of(isolated), 4, 1));
        assertEquals(states, LinkProtocol.parseNearby(text));
        LinkProtocol.GroupInfo g = LinkProtocol.parseNearbyGroup(text);
        assertEquals(Set.of(mate), g.mates());
        assertEquals(Set.of(isolated), g.isolated());
        assertEquals(4, g.groupHear());
        assertEquals(1, g.groupDeaf());

        LinkProtocol.GroupInfo old = LinkProtocol.parseNearbyGroup(LinkProtocol.nearby(states));
        assertFalse(old.hasTotals());
        assertTrue(old.mates().isEmpty() && old.isolated().isEmpty());
        assertEquals(LinkProtocol.GroupInfo.NONE, LinkProtocol.parseNearbyGroup(null));

        ServerLink link = new ServerLink();
        long t = 1_000_000_000L;
        link.onNearby(text, t);
        assertEquals(4, link.group(t).groupHear());
        assertFalse(link.group(t + java.util.concurrent.TimeUnit.MINUTES.toNanos(1)).hasTotals());
    }

    @Test
    @DisplayName("HUD settings parse leniently and cycle")
    void enums() {
        assertEquals(HudMode.ALWAYS, HudMode.fromId(" Always ", HudMode.OFF));
        assertEquals(HudMode.TALKING, HudMode.fromId("nope", HudMode.TALKING));
        assertEquals(HudMode.OFF, HudMode.ALWAYS.next());
        // Clockwise round the screen
        assertEquals(HudCorner.TOP_RIGHT, HudCorner.TOP_LEFT.next());
        assertEquals(HudCorner.BOTTOM_RIGHT, HudCorner.TOP_RIGHT.next());
        assertEquals(HudCorner.BOTTOM_LEFT, HudCorner.BOTTOM_RIGHT.next());
        assertEquals(HudCorner.TOP_LEFT, HudCorner.BOTTOM_LEFT.next());
        assertTrue(HudCorner.BOTTOM_RIGHT.isRight() && HudCorner.BOTTOM_RIGHT.isBottom());
    }
}
