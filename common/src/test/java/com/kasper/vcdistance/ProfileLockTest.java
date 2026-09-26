package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class ProfileLockTest {

    @Test
    @DisplayName("Parts parse from all, none and lists, and unknown parts are refused")
    void parseParts() {
        assertEquals(EnumSet.allOf(DistanceConfig.Part.class), DistanceConfig.Part.parseSet("all"));
        assertEquals(EnumSet.noneOf(DistanceConfig.Part.class), DistanceConfig.Part.parseSet("none"));
        assertEquals(EnumSet.of(DistanceConfig.Part.CURVE, DistanceConfig.Part.WALLS), DistanceConfig.Part.parseSet("Curve, walls"));
        assertNull(DistanceConfig.Part.parseSet("curve,echo"));
        assertEquals("curve,walls", DistanceConfig.Part.format(EnumSet.of(DistanceConfig.Part.WALLS, DistanceConfig.Part.CURVE)));
        assertEquals("all", DistanceConfig.Part.format(EnumSet.allOf(DistanceConfig.Part.class)));
        assertEquals("none", DistanceConfig.Part.format(EnumSet.noneOf(DistanceConfig.Part.class)));
    }

    @Test
    @DisplayName("Enforced: locked parts come from the server, the rest stays the player's own")
    void partialLock(@TempDir Path dir) {
        ServerSettings s = new ServerSettings(dir.resolve("server.properties"));
        s.setProfileMode(ServerSettings.ProfileMode.ENFORCE);
        s.profile().setModel(AttenuationModel.EXPONENTIAL);
        s.profile().setReverbEnabled(false);
        s.setWallsStrength(0.9);
        s.setLockedParts(EnumSet.of(DistanceConfig.Part.CURVE));
        s.setMonitorAllowed(false);

        ServerLink link = new ServerLink();
        link.onProfile(LinkProtocol.profile(s, 48.0, 24.0));
        DistanceConfig own = new DistanceConfig(dir.resolve("client.properties"));
        own.setModel(AttenuationModel.LINEAR);
        own.setReverbEnabled(true);
        own.setOcclusionStrength(0.3);
        own.setHudScale(1.4);

        DistanceConfig eff = link.effective(own);
        assertEquals(AttenuationModel.EXPONENTIAL, eff.getModel());
        assertTrue(eff.isReverbEnabled());
        assertEquals(0.3, eff.getOcclusionStrength(), 1e-9);
        assertEquals(1.4, eff.getHudScale(), 1e-9);
        assertTrue(link.isLocked(DistanceConfig.Part.CURVE));
        assertFalse(link.isLocked(DistanceConfig.Part.WALLS));
        assertFalse(link.isMonitorAllowed());

        // Same inputs, same object; a change of the player's own settings shows at once
        assertSame(eff, link.effective(own));
        own.setReverbEnabled(false);
        assertFalse(link.effective(own).isReverbEnabled());
    }

    @Test
    @DisplayName("A server before the lock setting locks everything and allows the monitor")
    void oldServer() {
        String text = "protocol=1\nmode=enforce\nprofile.distance_model=exponential\n";
        ServerLink link = new ServerLink();
        link.onProfile(text);
        for (DistanceConfig.Part p : DistanceConfig.Part.values()) {
            assertTrue(link.isLocked(p));
        }
        assertTrue(link.isMonitorAllowed());
        assertEquals(AttenuationModel.EXPONENTIAL, link.effective(new DistanceConfig(Path.of("x.properties"))).getModel());
    }

    @Test
    @DisplayName("Not enforced: nothing is locked")
    void suggestLocksNothing(@TempDir Path dir) {
        ServerSettings s = new ServerSettings(dir.resolve("server.properties"));
        s.setProfileMode(ServerSettings.ProfileMode.SUGGEST);
        ServerLink link = new ServerLink();
        link.onProfile(LinkProtocol.profile(s, 48.0, 24.0));
        DistanceConfig own = new DistanceConfig(dir.resolve("client.properties"));
        assertSame(own, link.effective(own));
        assertFalse(link.isLocked(DistanceConfig.Part.CURVE));
    }

    @Test
    @DisplayName("/vcd lock and /vcd monitor are saved to the file")
    void commands(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("server.properties");
        ServerSettings s = new ServerSettings(file);
        s.save();
        AdminCommands.Context ctx = new AdminCommands.Context() {
            public String platform() {
                return "Test";
            }

            public int onlinePlayers() {
                return 0;
            }

            public int addonPlayers() {
                return 0;
            }

            public void resendProfiles() {
            }
        };
        AdminCommands.run("lock curve,walls", s, ctx);
        AdminCommands.run("monitor off", s, ctx);
        assertTrue(AdminCommands.run("status", s, ctx).stream().anyMatch(l -> l.contains("curve,walls")));
        String text = Files.readString(file);
        assertTrue(text.contains("profile_locked=curve,walls\n"));
        assertTrue(text.contains("allow_monitor=false\n"));
        ServerSettings again = new ServerSettings(file);
        again.load();
        assertEquals(EnumSet.of(DistanceConfig.Part.CURVE, DistanceConfig.Part.WALLS), again.getLockedParts());
        assertFalse(again.isMonitorAllowed());
    }
}
