package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ServerToolsTest {

    @TempDir
    Path dir;

    private ServerSettings settings(String text) throws IOException {
        Path file = dir.resolve("server.properties");
        Files.writeString(file, text);
        ServerSettings s = new ServerSettings(file);
        s.load();
        return s;
    }

    @Test
    @DisplayName("Zones: worlds and regions from the file; a region wins over its world")
    void zones() throws IOException {
        ServerSettings s = settings("""
                profile_mode=suggest
                profile_preset=realistic
                zone.world.world_nether.profile_preset=stealth
                zone.world.the_end.profile_mode=enforce
                zone.region.Arena.profile_preset=clear
                zone.region.arena.profile_mode=enforce
                zone.region.bad.profile_preset=nope
                zone.nonsense=1
                """);
        assertEquals(3, s.zones().size());

        Zone nether = Zone.resolve(s.zones(), "world_nether", List.of());
        assertNotNull(nether);
        assertEquals("stealth", nether.preset());
        assertEquals(ServerSettings.ProfileMode.SUGGEST, s.modeIn(nether));
        assertEquals(AttenuationModel.EXPONENTIAL, s.profileIn(nether).getModel());
        assertEquals(AttenuationModel.REALISTIC_INVERSE, s.profileIn(null).getModel());

        // Fabric dimension ids match a zone written without the namespace
        Zone end = Zone.resolve(s.zones(), "minecraft:the_end", null);
        assertNotNull(end);
        assertEquals(ServerSettings.ProfileMode.ENFORCE, s.modeIn(end));

        Zone arena = Zone.resolve(s.zones(), "world_nether", List.of("spawn", "arena"));
        assertEquals("region:arena", arena.key());
        assertEquals("clear", arena.preset());
        assertEquals(ServerSettings.ProfileMode.ENFORCE, arena.mode());

        assertNull(Zone.resolve(s.zones(), "world", List.of("spawn")));

        // The zone's profile goes to the client with the zone's name
        LinkProtocol.ServerProfile p = LinkProtocol.parseProfile(LinkProtocol.profile(s, arena, 48, 24));
        assertEquals(ServerSettings.ProfileMode.ENFORCE, p.mode());
        assertEquals("arena", p.zone());
        // The zone's preset shapes the curve; walls stay as section 1 says
        assertEquals(0.35, p.config().getAttenuationFactor(), 1e-9);
        assertEquals(0.25, p.config().getMinVolumeFraction(), 1e-9);
        assertTrue(p.config().isOcclusionEnabled());

        // Saving keeps the zones
        s.save();
        ServerSettings again = new ServerSettings(s.getPath());
        again.load();
        assertEquals(s.zones(), again.zones());
    }

    @Test
    @DisplayName("Client announces entering and leaving a zone once")
    void zoneNotice() throws IOException {
        ServerSettings s = settings("zone.world.world_nether.profile_preset=stealth\n");
        ServerLink link = new ServerLink();
        link.onProfile(LinkProtocol.profile(s, null, 48, 24));
        assertNull(link.consumeZoneNotice());
        link.onProfile(LinkProtocol.profile(s, s.zones().get("world:world_nether"), 48, 24));
        assertEquals("world_nether", link.consumeZoneNotice());
        assertNull(link.consumeZoneNotice());
        link.onProfile(LinkProtocol.profile(s, null, 48, 24));
        assertEquals("", link.consumeZoneNotice());
    }

    @Test
    @DisplayName("Zone tracker reports only changes")
    void tracker() {
        ZoneTracker t = new ZoneTracker();
        UUID id = UUID.randomUUID();
        Zone a = new Zone(Zone.WORLD, "a", null, "clear");
        t.set(id, null);
        assertFalse(t.changed(id, null));
        assertTrue(t.changed(id, a));
        assertFalse(t.changed(id, a));
        assertTrue(t.changed(id, null));
        t.forget(id);
        assertTrue(t.changed(id, null));
    }

    @Test
    @DisplayName("/vcd changes, saves and resends; replies in the chosen language")
    void commands() throws IOException {
        ServerSettings s = settings("messages_language=ru\n");
        AtomicInteger resent = new AtomicInteger();
        AdminCommands.Context ctx = new AdminCommands.Context() {
            public String platform() {
                return "Test";
            }

            public int onlinePlayers() {
                return 5;
            }

            public int addonPlayers() {
                return 2;
            }

            public void resendProfiles() {
                resent.incrementAndGet();
            }
        };

        List<String> status = AdminCommands.run("", s, ctx);
        assertTrue(status.get(0).startsWith("Voice Physics"));
        assertTrue(status.stream().anyMatch(l -> l.contains("2 из 5")), status.toString());

        AdminCommands.run("profile enforce", s, ctx);
        AdminCommands.run("preset stealth", s, ctx);
        AdminCommands.run("walls 85", s, ctx);
        AdminCommands.run("serverwalls off", s, ctx);
        assertEquals(4, resent.get());

        ServerSettings saved = new ServerSettings(s.getPath());
        saved.load();
        assertEquals(ServerSettings.ProfileMode.ENFORCE, saved.getProfileMode());
        assertEquals("stealth", saved.getProfilePreset());
        assertEquals(0.85, saved.profile().getOcclusionStrength(), 1e-9);
        assertFalse(saved.isServerWalls());

        AdminCommands.run("walls off", s, ctx);
        assertFalse(s.profile().isOcclusionEnabled());

        List<String> bad = AdminCommands.run("profile loud", s, ctx);
        assertTrue(bad.get(0).startsWith("Использование"), bad.toString());
        assertEquals(5, resent.get());
        assertTrue(AdminCommands.run("help", s, ctx).size() > 5);
        assertTrue(AdminCommands.run("zones", s, ctx).get(0).startsWith("Зон нет"));

        assertEquals(List.of("preset", "profile"), AdminCommands.suggest("pr").stream().sorted().toList());
        assertEquals(List.of("stealth"), AdminCommands.suggest("preset st"));
        assertEquals(List.of("on", "off"), AdminCommands.suggest("serverwalls o"));
        assertEquals(0.6, AdminCommands.parsePercent("60%"), 1e-9);
        assertEquals(0.6, AdminCommands.parsePercent("0.6"), 1e-9);
        assertNull(AdminCommands.parsePercent("-5"));
    }
}
