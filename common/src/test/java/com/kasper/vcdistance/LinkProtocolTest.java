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
}
