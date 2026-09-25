package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
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
    @DisplayName("HUD settings parse leniently and cycle")
    void enums() {
        assertEquals(HudMode.ALWAYS, HudMode.fromId(" Always ", HudMode.OFF));
        assertEquals(HudMode.TALKING, HudMode.fromId("nope", HudMode.TALKING));
        assertEquals(HudMode.OFF, HudMode.ALWAYS.next());
        assertEquals(HudCorner.TOP_LEFT, HudCorner.BOTTOM_RIGHT.next());
        assertTrue(HudCorner.BOTTOM_RIGHT.isRight() && HudCorner.BOTTOM_RIGHT.isBottom());
    }
}
