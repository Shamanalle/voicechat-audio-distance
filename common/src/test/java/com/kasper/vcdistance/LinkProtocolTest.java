package com.kasper.vcdistance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.*;

public class LinkProtocolTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("Profile message round-trips mode, distances and every setting")
    void profileRoundTrip() {
        ServerSettings s = new ServerSettings(dir.resolve("s.properties"));
        s.setProfileMode(ServerSettings.ProfileMode.ENFORCE);
        s.profile().setModel(AttenuationModel.EXPONENTIAL);
        s.profile().setOcclusionStrength(0.9);
        s.profile().setMaterialWeight(AcousticMaterial.WOOL, 2.5);

        LinkProtocol.ServerProfile p = LinkProtocol.parseProfile(LinkProtocol.profile(s, 64.0, 16.0));
        assertNotNull(p);
        assertEquals(ServerSettings.ProfileMode.ENFORCE, p.mode());
        assertEquals(AttenuationModel.EXPONENTIAL, p.config().getModel());
        assertEquals(0.9, p.config().getOcclusionStrength(), 1e-4);
        assertEquals(2.5, p.config().getMaterialWeight(AcousticMaterial.WOOL), 1e-4);
        assertEquals(64.0, p.voiceDistance(), 1e-4);
        assertEquals(0.25, p.whisperShare(), 1e-4);
        assertTrue(p.serverWalls());
    }

    @Test
    @DisplayName("Garbage and oversized messages are rejected, hello is versioned")
    void rejectsGarbage() {
        assertNull(LinkProtocol.parseProfile(null));
        assertNull(LinkProtocol.parseProfile(""));
        assertNull(LinkProtocol.parseProfile("hello there"));
        assertNull(LinkProtocol.parseProfile("x".repeat(LinkProtocol.MAX_LENGTH + 1)));
        assertEquals(LinkProtocol.VERSION, LinkProtocol.parseHello(LinkProtocol.hello("1.2.0")));
        assertEquals(-1, LinkProtocol.parseHello("nonsense"));
    }

    @Test
    @DisplayName("Client link: suggest keeps own settings, enforce overrides until reset")
    void serverLink() {
        ServerSettings s = new ServerSettings(dir.resolve("s.properties"));
        s.profile().setAttenuationFactor(0.2);
        DistanceConfig own = new DistanceConfig(dir.resolve("c.properties"));
        ServerLink link = new ServerLink();
        assertSame(own, link.effective(own));
        assertEquals(0.5, link.whisperShare());

        s.setProfileMode(ServerSettings.ProfileMode.SUGGEST);
        link.onProfile(LinkProtocol.profile(s, 48, 24));
        assertTrue(link.isSuggested());
        assertSame(own, link.effective(own));
        assertTrue(link.consumeNotice());
        assertFalse(link.consumeNotice());

        s.setProfileMode(ServerSettings.ProfileMode.ENFORCE);
        link.onProfile(LinkProtocol.profile(s, 48, 24));
        assertTrue(link.isEnforced());
        assertEquals(0.2, link.effective(own).getAttenuationFactor(), 1e-4);
        assertEquals(DistanceConfig.DEFAULT_ATTENUATION_FACTOR, own.getAttenuationFactor(), 1e-9, "own config untouched");

        link.reset();
        assertFalse(link.isConnected());
        assertSame(own, link.effective(own));
    }

    @Test
    @DisplayName("Server settings: defaults written, edits picked up by reload")
    void serverSettingsReload() throws IOException {
        Path file = dir.resolve("server.properties");
        ServerSettings s = new ServerSettings(file);
        s.load();
        assertTrue(Files.exists(file));
        assertEquals(ServerSettings.ProfileMode.OFF, s.getProfileMode());
        assertTrue(s.isServerWalls());
        assertFalse(s.reloadIfChanged());

        String text = Files.readString(file)
                .replace("profile_mode=off", "profile_mode=enforce")
                .replace("server_walls_max_streams=24", "server_walls_max_streams=9999");
        Files.writeString(file, text);
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5000));
        assertTrue(s.reloadIfChanged());
        assertEquals(ServerSettings.ProfileMode.ENFORCE, s.getProfileMode());
        assertEquals(ServerSettings.MAX_STREAMS_LIMIT, s.getMaxStreams());
    }

    @Test
    @DisplayName("Wire form matches Minecraft's string encoding (VarInt byte length + UTF-8)")
    void wireFormat() {
        byte[] ascii = LinkProtocol.encode("abc");
        assertArrayEquals(new byte[]{3, 'a', 'b', 'c'}, ascii);

        // 200 bytes need a two-byte VarInt: 0xC8 0x01
        String longText = "x".repeat(200);
        byte[] encoded = LinkProtocol.encode(longText);
        assertEquals((byte) 0xC8, encoded[0]);
        assertEquals(1, encoded[1]);
        assertEquals(longText, LinkProtocol.decode(encoded));

        // Length counts bytes, not characters
        String cyrillic = "Громкость";
        byte[] ru = LinkProtocol.encode(cyrillic);
        assertEquals(cyrillic.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, ru[0]);
        assertEquals(cyrillic, LinkProtocol.decode(ru));

        ServerSettings s = new ServerSettings(dir.resolve("w.properties"));
        String profile = LinkProtocol.profile(s, 48.0, 24.0);
        assertEquals(profile, LinkProtocol.decode(LinkProtocol.encode(profile)));
    }

    @Test
    @DisplayName("Malformed wire data is rejected")
    void wireRejectsGarbage() {
        assertNull(LinkProtocol.decode(null));
        assertNull(LinkProtocol.decode(new byte[0]));
        assertNull(LinkProtocol.decode(new byte[]{5, 'a', 'b'}));
        assertNull(LinkProtocol.decode(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 1}));
        assertThrows(IllegalArgumentException.class, () -> LinkProtocol.encode("x".repeat(LinkProtocol.MAX_LENGTH + 1)));
    }

    @Test
    @DisplayName("Server file: sections, a preset by name, walls shared by everyone")
    void serverSettingsPreset() throws IOException {
        Path file = dir.resolve("preset.properties");
        ServerSettings s = new ServerSettings(file);
        s.load();
        String text = Files.readString(file);
        assertTrue(text.indexOf("walls_strength=") < text.indexOf("server_walls=")
                && text.indexOf("server_walls=") < text.indexOf("profile_mode="), "sections in order");
        assertTrue(text.contains("Игроки без аддона"));

        text = text.replace("profile_preset=custom", "profile_preset=Stealth")
                .replace("walls_strength=0.6", "walls_strength=0.9")
                .replace("material.wool=1.4", "material.wool=2.5");
        Files.writeString(file, text);
        s.load();
        assertEquals("stealth", s.getProfilePreset());
        assertEquals(AttenuationModel.EXPONENTIAL, s.profile().getModel());
        // Walls come from section 1, not from the preset
        assertTrue(s.profile().isOcclusionEnabled());
        assertEquals(0.9, s.profile().getOcclusionStrength(), 1e-9);
        assertEquals(2.5, s.profile().getMaterialWeight(AcousticMaterial.WOOL), 1e-9);

        // The protocol carries the result to the addon
        LinkProtocol.ServerProfile p = LinkProtocol.parseProfile(LinkProtocol.profile(s, 48, 24));
        assertEquals(AttenuationModel.EXPONENTIAL, p.config().getModel());
        assertEquals(0.9, p.config().getOcclusionStrength(), 1e-4);

        Files.writeString(file, Files.readString(file)
                .replace("profile_preset=Stealth", "profile_preset=nonsense")
                .replace("walls_strength=0.9", "walls_strength=0"));
        s.load();
        assertEquals(ServerSettings.CUSTOM_PRESET, s.getProfilePreset());
        assertFalse(s.profile().isOcclusionEnabled());
    }

    @Test
    @DisplayName("A 1.2.0 server file is rewritten in the new format with its values kept")
    void serverSettingsMigration() throws IOException {
        Path file = dir.resolve("old.properties");
        Files.writeString(file, String.join("\n",
                "#VoiceChat Audio Distance - server settings",
                "profile_mode=suggest",
                "server_walls=false",
                "server_walls_max_streams=40",
                "profile.distance_model=realistic_inverse",
                "profile.occlusion_enabled=true",
                "profile.occlusion_strength=0.8500",
                "profile.material.glass=1.2000"));
        ServerSettings s = new ServerSettings(file);
        s.load();
        assertEquals(ServerSettings.ProfileMode.SUGGEST, s.getProfileMode());
        assertFalse(s.isServerWalls());
        assertEquals(40, s.getMaxStreams());
        assertEquals(AttenuationModel.REALISTIC_INVERSE, s.profile().getModel());
        assertEquals(0.85, s.profile().getOcclusionStrength(), 1e-9);
        assertEquals(1.2, s.profile().getMaterialWeight(AcousticMaterial.GLASS), 1e-9);

        String text = Files.readString(file);
        assertTrue(text.contains("settings_version=7"));
        assertTrue(text.contains("profile_locked=all\n"));
        assertTrue(text.contains("allow_monitor=true\n"));
        assertTrue(text.contains("profile.reverb_enabled=true\n"));
        assertTrue(text.contains("walls_strength=0.85\n"));
        assertTrue(text.contains("material.glass=1.2\n"));
        assertTrue(text.contains("profile.distance_model=realistic_inverse\n"));
        assertTrue(text.contains("server_walls_max_streams=40\n"));

        // Reading the rewritten file gives the same settings
        ServerSettings again = new ServerSettings(file);
        again.load();
        assertEquals(0.85, again.profile().getOcclusionStrength(), 1e-9);
        assertEquals(ServerSettings.ProfileMode.SUGGEST, again.getProfileMode());
    }
}
