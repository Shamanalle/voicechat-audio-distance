package com.kasper.vcdistance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Messages exchanged between the addon on the client and on the server. Both sides are optional:
 * without the other side the addon simply works on its own.
 * <ul>
 *     <li>{@code hello} (client to server): "I have the addon and process walls myself".</li>
 *     <li>{@code profile} (server to client): the server's sound profile, how it is offered, and the
 *     server's real voice and whisper distances.</li>
 * </ul>
 * Payloads are plain {@link Properties} text, so both sides tolerate unknown or missing keys.
 * On the wire each payload is one Minecraft string (VarInt byte length, then UTF-8), which is what
 * the Fabric builds write and what {@link #encode} / {@link #decode} produce for Bukkit plugin messages.
 */
public final class LinkProtocol {

    public static final int VERSION = 1;
    public static final String NAMESPACE = "vc-audio-distance";
    public static final String HELLO = "hello";
    public static final String PROFILE = "profile";
    /** Upper bound for a payload string; profiles are well under 2 KB. */
    public static final int MAX_LENGTH = 16384;

    private static final String PROFILE_PREFIX = "profile.";

    private LinkProtocol() {
    }

    /** What a client learns from a server that has the addon. */
    public record ServerProfile(ServerSettings.ProfileMode mode, DistanceConfig config,
                                double voiceDistance, double whisperDistance, boolean serverWalls) {

        /** Whisper range as a share of the voice range, for the distance graph. */
        public double whisperShare() {
            if (voiceDistance > 0.0 && whisperDistance > 0.0) {
                return Math.max(0.05, Math.min(1.0, whisperDistance / voiceDistance));
            }
            return 0.5;
        }
    }

    public static String hello(String modVersion) {
        Properties p = new Properties();
        p.setProperty("protocol", String.valueOf(VERSION));
        p.setProperty("mod_version", modVersion == null ? "unknown" : modVersion);
        return write(p);
    }

    /** @return the protocol version of a hello message, or -1 when it is not one */
    public static int parseHello(String text) {
        Properties p = read(text);
        if (p == null) {
            return -1;
        }
        return (int) DistanceConfig.parseDouble(p, "protocol", -1);
    }

    public static String profile(ServerSettings settings, double voiceDistance, double whisperDistance) {
        Properties p = new Properties();
        p.setProperty("protocol", String.valueOf(VERSION));
        p.setProperty("mode", settings.getProfileMode().getId());
        p.setProperty("voice_distance", DistanceConfig.format(voiceDistance));
        p.setProperty("whisper_distance", DistanceConfig.format(whisperDistance));
        p.setProperty("server_walls", String.valueOf(settings.isServerWalls()));
        settings.profile().writeTo(p, PROFILE_PREFIX);
        return write(p);
    }

    /** @return the profile, or {@code null} when the text is not a valid profile message */
    public static ServerProfile parseProfile(String text) {
        Properties p = read(text);
        if (p == null || DistanceConfig.parseDouble(p, "protocol", -1) < 1) {
            return null;
        }
        DistanceConfig config = new DistanceConfig(Path.of("server-profile.properties"));
        config.readFrom(p, PROFILE_PREFIX);
        return new ServerProfile(
                ServerSettings.ProfileMode.fromId(p.getProperty("mode"), ServerSettings.ProfileMode.OFF),
                config,
                DistanceConfig.parseDouble(p, "voice_distance", 0.0),
                DistanceConfig.parseDouble(p, "whisper_distance", 0.0),
                DistanceConfig.parseBoolean(p, "server_walls", false));
    }

    /** Wire form of a payload: VarInt length in bytes, then the UTF-8 text (Minecraft's string encoding). */
    public static byte[] encode(String text) {
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Payload too long: " + text.length());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(utf8.length + 3);
        int value = utf8.length;
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
        out.write(utf8, 0, utf8.length);
        return out.toByteArray();
    }

    /** @return the text of a payload in wire form, or {@code null} when it is malformed or too long */
    public static String decode(byte[] data) {
        if (data == null) {
            return null;
        }
        int length = 0;
        int index = 0;
        for (int shift = 0; ; shift += 7) {
            if (index >= data.length || shift > 28) {
                return null;
            }
            byte b = data[index++];
            length |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
        }
        // Same bound Minecraft applies: at most 3 bytes per character
        if (length < 0 || length > MAX_LENGTH * 3 || data.length - index < length) {
            return null;
        }
        String text = new String(data, index, length, StandardCharsets.UTF_8);
        return text.length() > MAX_LENGTH ? null : text;
    }

    private static String write(Properties p) {
        StringWriter w = new StringWriter();
        try {
            p.store(w, null);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        // Drop the timestamp comment Properties always writes
        StringBuilder out = new StringBuilder();
        for (String line : w.toString().split("\n")) {
            if (!line.startsWith("#")) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private static Properties read(String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_LENGTH) {
            return null;
        }
        Properties p = new Properties();
        try {
            p.load(new StringReader(text));
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
        return p;
    }
}
