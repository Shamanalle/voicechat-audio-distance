package com.kasper.vcdistance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 1.8 server features: boxes and zone rules, voice range rules, the addon requirement, the new commands. */
public class ServerRulesTest {

    @TempDir
    Path dir;

    private final ServerPlayers players = new ServerPlayers();

    @AfterEach
    void clear() {
        players.clear();
    }

    private ServerSettings settings(String text) throws IOException {
        Path file = dir.resolve("server.properties");
        Files.writeString(file, text);
        ServerSettings s = new ServerSettings(file);
        s.load();
        return s;
    }

    private static ServerPlayers.Info player(String name, String world, double x, double z) {
        return player(name, world, x, z, false, true, false, "");
    }

    private static ServerPlayers.Info player(String name, String world, double x, double z,
                                             boolean sneaking, boolean alive, boolean spectator, String hand) {
        return new ServerPlayers.Info(UUID.nameUUIDFromBytes(name.getBytes()), name, world, x, 64, z,
                sneaking, alive, spectator, hand, "", List.of(), "");
    }

    private AdminCommands.Context ctx(UUID sender) {
        return new AdminCommands.Context() {
            public String platform() {
                return "Test";
            }

            public int onlinePlayers() {
                return players.all().size();
            }

            public int addonPlayers() {
                return 0;
            }

            public void resendProfiles() {
            }

            public UUID sender() {
                return sender;
            }

            public ServerPlayers players() {
                return players;
            }
        };
    }

    @Test
    @DisplayName("Boxes: read from the file, found by position, the smaller or higher-priority box wins, saved back")
    void boxes() throws IOException {
        ServerSettings s = settings("""
                zone.box.stage.world=world
                zone.box.stage.from=0,60,0
                zone.box.stage.to=20,80,20
                zone.box.stage.range_multiplier=2
                zone.box.booth.world=world
                zone.box.booth.from=5,60,5
                zone.box.booth.to=6,70,6
                zone.box.booth.isolated=true
                zone.box.booth.echo=off
                zone.box.hall.world=world
                zone.box.hall.from=-100,0,-100
                zone.box.hall.to=100,200,100
                zone.box.hall.priority=5
                zone.box.hall.echo=0.8
                zone.box.hall.enter_message=Welcome to the hall
                zone.box.broken.world=world
                zone.world.world_nether.voice_range=16
                """);
        assertEquals(4, s.zones().size(), s.zones().keySet().toString());
        assertEquals("hall", s.zoneOf(player("a", "world", 10, 10)).name(), "priority 5 wins over smaller boxes");

        s.putZone(new Zone(Zone.BOX, "hall", null, null, Zone.Rules.NONE, new Zone.Box("world", -100, 0, -100, 100, 200, 100), 0));
        assertEquals("booth", s.zoneOf(player("a", "world", 5.5, 5.5)).name(), "the smallest box wins on equal priority");
        assertEquals("stage", s.zoneOf(player("a", "world", 15, 15)).name());
        assertEquals("hall", s.zoneOf(player("a", "world", 50, 50)).name());
        assertNull(s.zoneOf(player("a", "world", 500, 500)));
        assertEquals(16.0, s.zoneOf(player("a", "world_nether", 0, 0)).rules().voiceRange());

        s.save();
        ServerSettings again = new ServerSettings(s.getPath());
        again.load();
        assertEquals(s.zones(), again.zones());
        Zone booth = again.zones().get("box:booth");
        assertTrue(booth.rules().isolated());
        assertEquals(0.0, booth.rules().echo());
    }

    @Test
    @DisplayName("Zone profiles carry walls, echo and the entry message to players with the addon")
    void zoneProfile() throws IOException {
        ServerSettings s = settings("""
                zone.box.cathedral.world=world
                zone.box.cathedral.from=0,0,0
                zone.box.cathedral.to=10,10,10
                zone.box.cathedral.echo=0.9
                zone.box.cathedral.walls_strength=0.2
                zone.box.cathedral.enter_message=Quiet, please
                """);
        Zone z = s.zones().get("box:cathedral");
        LinkProtocol.ServerProfile p = LinkProtocol.parseProfile(LinkProtocol.profile(s, z, 48, 24, true));
        assertEquals("cathedral", p.zone());
        assertEquals("Quiet, please", p.zoneMessage());
        assertEquals(0.9, p.echo(), 1e-4);
        assertTrue(p.admin());
        assertEquals(0.2, p.config().getOcclusionStrength(), 1e-4);
        assertTrue(p.config().isReverbEnabled());
        assertFalse(LinkProtocol.parseProfile(LinkProtocol.profile(s, null, 48, 24)).admin());
        assertEquals(0.2, s.wallsStrengthIn(z), 1e-9);
    }

    @Test
    @DisplayName("Voice range: zone ranges, sneaking and the megaphone")
    void ranges() throws IOException {
        ServerSettings s = settings("""
                sneak_range_multiplier=0.5
                megaphone_item=goat_horn
                megaphone_multiplier=3
                zone.box.stage.world=world
                zone.box.stage.from=0,0,0
                zone.box.stage.to=10,100,10
                zone.box.stage.range_multiplier=2
                zone.box.library.world=world
                zone.box.library.from=100,0,100
                zone.box.library.to=110,100,110
                zone.box.library.voice_range=8
                """);
        assertEquals("minecraft:goat_horn", s.getMegaphoneItem());
        assertTrue(s.hasVoiceRules());
        assertEquals(48.0, ServerRange.rangeOf(s, player("a", "world", 500, 500), false, 48, 24), 1e-9);
        assertEquals(96.0, ServerRange.rangeOf(s, player("a", "world", 5, 5), false, 48, 24), 1e-9);
        assertEquals(48.0, ServerRange.rangeOf(s, player("a", "world", 5, 5), true, 48, 24), 1e-9);
        assertEquals(8.0, ServerRange.rangeOf(s, player("a", "world", 105, 105), false, 48, 24), 1e-9);
        assertEquals(4.0, ServerRange.rangeOf(s, player("a", "world", 105, 105), true, 48, 24), 1e-9, "whispers scale with the zone's voice range");
        assertEquals(24.0, ServerRange.rangeOf(s, player("a", "world", 500, 500, true, true, false, ""), false, 48, 24), 1e-9);
        assertEquals(144.0, ServerRange.rangeOf(s, player("a", "world", 500, 500, false, true, false, "minecraft:goat_horn"), false, 48, 24), 1e-9);

        ServerSettings plain = settings("");
        assertFalse(plain.hasVoiceRules(), "without rules voice packets are left alone");
    }

    @Test
    @DisplayName("Who hears whom: range, isolated zones, dead players, spectators, other worlds")
    void decisions() throws IOException {
        ServerSettings s = settings("""
                dead_players_silent=true
                spectators_hear_only_spectators=true
                zone.box.booth.world=world
                zone.box.booth.from=0,0,0
                zone.box.booth.to=4,100,4
                zone.box.booth.isolated=true
                """);
        ServerPlayers.Info a = player("a", "world", 50, 50);
        ServerPlayers.Info near = player("b", "world", 60, 50);
        ServerPlayers.Info far = player("c", "world", 150, 50);
        ServerPlayers.Info inBooth = player("d", "world", 2, 2);
        ServerPlayers.Info boothMate = player("e", "world", 3, 3);
        ServerPlayers.Info outside = player("f", "world", 6, 2);
        ServerPlayers.Info dead = player("g", "world", 51, 50, false, false, false, "");
        ServerPlayers.Info ghost = player("h", "world", 52, 50, false, true, true, "");
        ServerPlayers.Info nether = player("i", "world_nether", 50, 50);

        assertEquals(ServerRange.Reason.HEARS, ServerRange.decide(s, a, near, false, 48, 24).reason());
        assertEquals(ServerRange.Reason.RANGE, ServerRange.decide(s, a, far, false, 48, 24).reason());
        assertEquals(ServerRange.Reason.HEARS, ServerRange.decide(s, inBooth, boothMate, false, 48, 24).reason());
        assertEquals(ServerRange.Reason.ISOLATED, ServerRange.decide(s, inBooth, outside, false, 48, 24).reason());
        assertEquals(ServerRange.Reason.ISOLATED, ServerRange.decide(s, outside, inBooth, false, 48, 24).reason());
        assertEquals(ServerRange.Reason.DEAD, ServerRange.decide(s, dead, a, false, 48, 24).reason());
        assertEquals(ServerRange.Reason.SPECTATOR, ServerRange.decide(s, ghost, a, false, 48, 24).reason());
        assertEquals(ServerRange.Reason.HEARS, ServerRange.decide(s, a, ghost, false, 48, 24).reason(), "spectators still hear players");
        assertEquals(ServerRange.Reason.WORLD, ServerRange.decide(s, a, nether, false, 48, 24).reason());
        assertEquals(48.0, ServerRange.decide(s, a, near, false, 48, 24).distance(), 1e-9);
    }

    @Test
    @DisplayName("Addon requirement: waits after joining, ignores players without voice chat, compares versions")
    void addonCheck() throws IOException {
        ServerSettings s = settings("require_addon=kick\nmin_addon_version=1.8.0\n");
        AddonCheck check = new AddonCheck();
        UUID none = UUID.randomUUID();
        UUID old = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        UUID noVoice = UUID.randomUUID();
        for (UUID u : new UUID[]{none, old, fresh, noVoice}) {
            check.joined(u, 0);
        }
        check.hello(old, "1.7.0+mc26.x");
        check.hello(fresh, "1.8.0+mc1.21.x");
        assertEquals(AddonCheck.Action.NONE, check.due(s, none, 10, true), "too early");
        assertEquals(AddonCheck.Action.KICK, check.due(s, none, AddonCheck.DELAY_TICKS, true));
        assertEquals(AddonCheck.Action.NONE, check.due(s, none, AddonCheck.DELAY_TICKS + 1, true), "once per join");
        assertEquals(AddonCheck.Action.KICK, check.due(s, old, AddonCheck.DELAY_TICKS, true));
        assertEquals(AddonCheck.Action.NONE, check.due(s, fresh, AddonCheck.DELAY_TICKS, true));
        assertEquals(AddonCheck.Action.NONE, check.due(s, noVoice, AddonCheck.DELAY_TICKS, false));

        s.setRequireAddon(ServerSettings.RequireAddon.SUGGEST);
        check.joined(none, 0);
        assertEquals(AddonCheck.Action.MESSAGE, check.due(s, none, AddonCheck.DELAY_TICKS, true));
        check.joined(none, 1000);
        assertEquals(AddonCheck.Action.NONE, check.due(s, none, 1000 + AddonCheck.DELAY_TICKS, true), "suggest tells once per server run");

        assertTrue(AddonCheck.compare("1.10.0", "1.9.9") > 0);
        assertEquals(0, AddonCheck.compare("1.8", "1.8.0+mc26.x"));
        assertTrue(AddonCheck.satisfied("2.0.0", ""));
        assertFalse(AddonCheck.satisfied(null, ""));
    }

    @Test
    @DisplayName("/vcd zone, rule, require and debug; replies in the admin's own language")
    void commands() throws IOException {
        ServerSettings s = settings("");
        ServerPlayers.Info admin = new ServerPlayers.Info(UUID.randomUUID(), "Admin", "world", 10.5, 64, 10.5,
                false, true, false, "", "", List.of(), "de_de");
        players.update(admin);
        players.update(player("Bob", "world", 20, 10));
        AdminCommands.Context ctx = ctx(admin.id());

        List<String> reply = AdminCommands.run("zone create stage", s, ctx);
        assertTrue(reply.get(0).startsWith("Markiere zuerst"), reply.toString());
        AdminCommands.run("zone pos1", s, ctx);
        players.update(new ServerPlayers.Info(admin.id(), "Admin", "world", 30, 70, 40, false, true, false, "", "", List.of(), "de_de"));
        AdminCommands.run("zone pos2", s, ctx);
        reply = AdminCommands.run("zone create Stage", s, ctx);
        assertTrue(reply.get(0).contains("10,64,10") && reply.get(0).contains("30,70,40"), reply.toString());
        AdminCommands.run("zone set stage range_multiplier 2", s, ctx);
        AdminCommands.run("zone set stage message Showtime!", s, ctx);
        AdminCommands.run("zone set stage echo 70%", s, ctx);
        assertTrue(AdminCommands.run("zone set stage walls loud", s, ctx).get(0).contains("loud"));
        Zone stage = s.zones().get("box:stage");
        assertEquals(2.0, stage.rules().rangeMultiplier());
        assertEquals("Showtime!", stage.rules().enterMessage());
        assertEquals(0.7, stage.rules().echo(), 1e-9);
        assertTrue(AdminCommands.run("zone info", s, ctx).get(0).contains("stage"));

        AdminCommands.run("zone set world_nether voice_range 16", s, ctx);
        assertEquals(16.0, s.zones().get("world:world_nether").rules().voiceRange());
        AdminCommands.run("zone set world_nether voice_range default", s, ctx);
        assertNull(s.zones().get("world:world_nether"), "a world zone with nothing left is removed");

        AdminCommands.run("rule sneak 0.4", s, ctx);
        AdminCommands.run("rule dead on", s, ctx);
        AdminCommands.run("rule megaphone goat_horn", s, ctx);
        AdminCommands.run("require warn 1.8", s, ctx);
        ServerSettings saved = new ServerSettings(s.getPath());
        saved.load();
        assertEquals(0.4, saved.getSneakMultiplier(), 1e-9);
        assertTrue(saved.isDeadSilent());
        assertEquals("minecraft:goat_horn", saved.getMegaphoneItem());
        assertEquals(ServerSettings.RequireAddon.WARN, saved.getRequireAddon());
        assertEquals("1.8", saved.getMinAddonVersion());
        assertEquals(s.zones(), saved.zones());

        List<String> debug = AdminCommands.run("debug bob", s, ctx);
        assertTrue(debug.get(0).contains("Bob"), debug.toString());
        assertTrue(debug.stream().anyMatch(l -> l.startsWith("Admin")), debug.toString());

        AdminCommands.run("zone delete stage", s, ctx);
        assertTrue(s.zones().isEmpty());

        // The console gets the configured language, English by default
        assertTrue(AdminCommands.run("zone pos1", s, ctx(null)).get(0).startsWith("Only a player"));
        s.load();
        assertEquals("auto", s.getMessagesLanguage());
        assertEquals(List.of("pos1", "pos2"), AdminCommands.suggest("zone pos"));
        assertEquals(List.of("range_multiplier"), AdminCommands.suggest("zone set stage ran"));
    }

    @Test
    @DisplayName("Server texts: every language file has them, and language codes are matched loosely")
    void serverText() {
        assertEquals("ru_ru", ServerText.language("ru"));
        assertEquals("pt_br", ServerText.language("pt_PT"));
        assertEquals("es_es", ServerText.language("es-MX"));
        assertEquals("en_us", ServerText.language("fr_fr"));
        assertEquals("en_us", ServerText.language(null));
        for (String l : ServerText.LANGUAGES) {
            String t = ServerText.get(l, "usage", "/vcd x");
            assertTrue(t.contains("/vcd x") && !t.startsWith("usage"), l + ": " + t);
        }
        assertEquals("Zone a: b, from c to d. Saved. Change it with /vcd zone set a <setting> <value>.",
                ServerText.get("en_us", "zone.created", "a", "b", "c", "d"));
        Map<String, String> parsed = ServerText.parse("{\"a\": \"x\\\"y\\n\", \"b\":\"\\u00e9\"}");
        assertEquals("x\"y\n", parsed.get("a"));
        assertEquals("é", parsed.get("b"));
    }

    @Test
    @DisplayName("Admins can change server texts and add languages in a folder next to the settings file")
    void editableTexts() throws IOException {
        ServerSettings s = settings("");
        Path folder = dir.resolve(ServerText.FOLDER);
        assertTrue(Files.isRegularFile(folder.resolve("ru_ru.json")), "written on first start");
        assertTrue(Files.readString(folder.resolve("ru_ru.json")).contains("\"command.vc-audio-distance.usage\""));
        assertFalse(Files.readString(folder.resolve("en_us.json")).contains("gui.vc-audio-distance"), "server texts only");
        assertEquals(ServerText.parse(Files.readString(folder.resolve("de_de.json"))).get("command.vc-audio-distance.usage"),
                ServerText.get("de_de", "usage"));

        Files.writeString(folder.resolve("en_us.json"), "{\"command.vc-audio-distance.usage\": \"How to: %s\"}");
        Files.writeString(folder.resolve("fr_fr.json"), "{\"command.vc-audio-distance.usage\": \"Utilisation : %s\"}");
        s.load();
        assertEquals("How to: /x", ServerText.get("en_us", "usage", "/x"));
        assertEquals("Utilisation : /x", ServerText.get("fr_fr", "usage", "/x"));
        assertEquals("fr_fr", ServerText.language("fr_FR"));
        // Lines the admin's file does not have come from the built-in texts
        assertTrue(ServerText.get("fr_fr", "zones.none").startsWith("No zones"));
        ServerText.useFolder(null);
    }

    @Test
    @DisplayName("Admin messages for the Server tab survive the trip")
    void adminProtocol() throws IOException {
        ServerSettings s = settings("require_addon=warn\nzone.box.x.world=w\nzone.box.x.from=0,0,0\nzone.box.x.to=1,1,1\n");
        assertEquals("zone info", LinkProtocol.parseAdminRequest(LinkProtocol.adminRequest("zone info")));
        LinkProtocol.AdminReply r = LinkProtocol.parseAdminReply(LinkProtocol.adminReply(List.of("one", "two"), s));
        assertEquals(List.of("one", "two"), r.lines());
        assertEquals("warn", r.state().getProperty("require_addon"));
        assertTrue(r.state().getProperty("zone.0").startsWith("box|x|"), r.state().toString());
        assertEquals("1.8.0", LinkProtocol.helloVersion(LinkProtocol.hello("1.8.0")));
    }
}
