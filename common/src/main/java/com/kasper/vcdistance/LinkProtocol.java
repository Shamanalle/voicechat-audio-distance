package com.kasper.vcdistance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Messages exchanged between the addon on the client and on the server. Both sides are optional:
 * without the other side the addon simply works on its own.
 * <ul>
 *     <li>{@code hello} (client to server): "I have the addon and process walls myself".</li>
 *     <li>{@code profile} (server to client): the server's sound profile, how it is offered, and the
 *     server's real voice and whisper distances.</li>
 *     <li>{@code nearby} (server to client, about once a second): the voice chat state of the
 *     players within voice range, for the monitor, and who in the receiver's Simple Voice Chat group
 *     hears them (see {@link GroupInfo}).</li>
 *     <li>{@code admin} (client to server) and {@code admin_reply} (server to client): the Server tab
 *     for server admins, which runs the same commands as {@code /vcd}.</li>
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
    public static final String NEARBY = "nearby";
    /** Client to server: a {@code /vcd} command from the Server tab (the server checks the player's rights). */
    public static final String ADMIN = "admin";
    /** Server to client: the command's reply and the server's settings, for the Server tab. */
    public static final String ADMIN_REPLY = "admin_reply";
    /** Most players one {@code nearby} message lists (the closest ones); keeps it far below {@link #MAX_LENGTH}. */
    public static final int MAX_NEARBY = 64;
    /** Upper bound for a payload string; profiles are well under 2 KB. */
    public static final int MAX_LENGTH = 16384;

    private static final String PROFILE_PREFIX = "profile.";
    private static final String PLAYER_PREFIX = "player.";
    private static final String MATE_PREFIX = "mate.";
    private static final String ISOLATED_PREFIX = "isolated.";

    /**
     * The receiver's Simple Voice Chat group, as the {@code nearby} message describes it. Clients
     * before 2.0.3 read only the {@code player.*} keys and ignore these.
     *
     * @param mates     listed nearby players in the receiver's group
     * @param isolated  listed nearby players in another, isolated group (they do not hear the receiver)
     * @param groupHear other members of the receiver's group, anywhere, who hear it; -1 when not in a group or unknown
     * @param groupDeaf other members who cannot (sound off, disconnected); -1 when unknown
     */
    public record GroupInfo(Set<UUID> mates, Set<UUID> isolated, int groupHear, int groupDeaf) {
        public static final GroupInfo NONE = new GroupInfo(Set.of(), Set.of(), -1, -1);

        public boolean hasTotals() {
            return groupHear >= 0 && groupDeaf >= 0;
        }
    }

    private LinkProtocol() {
    }

    /** What a client learns from a server that has the addon. */
    /**
     * @param zone        the zone's name, or {@code null} outside zones
     * @param zoneMessage what to show on entering the zone instead of its name, or {@code null}
     * @param echo        {@code null} = measure the room as usual, otherwise the echo size (0 - 1) the zone sets
     * @param admin       the player may change the server's settings (the Server tab is shown)
     */
    public record ServerProfile(ServerSettings.ProfileMode mode, DistanceConfig config,
                                double voiceDistance, double whisperDistance, boolean serverWalls, String zone,
                                String zoneMessage, Double echo, boolean admin,
                                java.util.Set<DistanceConfig.Part> locked, boolean monitor) {

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
        return profile(settings, null, voiceDistance, whisperDistance);
    }

    /** The profile as it applies in {@code zone} ({@code null}: the server's main profile). */
    public static String profile(ServerSettings settings, Zone zone, double voiceDistance, double whisperDistance) {
        return profile(settings, zone, voiceDistance, whisperDistance, false);
    }

    /** As above, telling the client whether its player is a server admin. */
    public static String profile(ServerSettings settings, Zone zone, double voiceDistance, double whisperDistance, boolean admin) {
        Properties p = new Properties();
        p.setProperty("protocol", String.valueOf(VERSION));
        p.setProperty("mode", settings.modeIn(zone).getId());
        p.setProperty("voice_distance", DistanceConfig.format(voiceDistance));
        p.setProperty("whisper_distance", DistanceConfig.format(whisperDistance));
        p.setProperty("server_walls", String.valueOf(settings.isServerWalls()));
        if (zone != null) {
            p.setProperty("zone", zone.name());
            if (zone.rules().enterMessage() != null) {
                p.setProperty("zone_message", zone.rules().enterMessage());
            }
            if (zone.rules().echo() != null) {
                p.setProperty("zone_echo", DistanceConfig.format(zone.rules().echo()));
            }
        }
        if (admin) {
            p.setProperty("admin", "true");
        }
        p.setProperty("locked", DistanceConfig.Part.format(settings.getLockedParts()));
        p.setProperty("monitor", String.valueOf(settings.isMonitorAllowed()));
        settings.profileIn(zone, voiceDistance).writeTo(p, PROFILE_PREFIX);
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
                DistanceConfig.parseBoolean(p, "server_walls", false),
                p.getProperty("zone"),
                p.getProperty("zone_message"),
                p.getProperty("zone_echo") == null ? null
                        : DistanceConfig.clamp(DistanceConfig.parseDouble(p, "zone_echo", 0.0), 0.0, 1.0),
                DistanceConfig.parseBoolean(p, "admin", false),
                // Servers before 1.9.0 lock the whole profile and allow the monitor
                lockedOf(p.getProperty("locked")),
                DistanceConfig.parseBoolean(p, "monitor", true));
    }

    private static java.util.Set<DistanceConfig.Part> lockedOf(String text) {
        java.util.Set<DistanceConfig.Part> parts = DistanceConfig.Part.parseSet(text);
        return parts == null ? java.util.EnumSet.allOf(DistanceConfig.Part.class) : parts;
    }

    /** The addon version in a hello message ("1.8.0+mc26.x"), or "" when missing. */
    public static String helloVersion(String text) {
        Properties p = read(text);
        String v = p == null ? null : p.getProperty("mod_version");
        return v == null ? "" : v.trim();
    }

    /** A command from the Server tab, as typed after {@code /vcd}. */
    public static String adminRequest(String command) {
        Properties p = new Properties();
        p.setProperty("protocol", String.valueOf(VERSION));
        p.setProperty("command", command == null ? "" : command);
        return write(p);
    }

    /** @return the command, or {@code null} when the text is not an admin request */
    public static String parseAdminRequest(String text) {
        Properties p = read(text);
        if (p == null || DistanceConfig.parseDouble(p, "protocol", -1) < 1) {
            return null;
        }
        return p.getProperty("command");
    }

    /** What the Server tab shows: the reply lines and the server's settings ({@link ServerSettings#writeState}). */
    public record AdminReply(java.util.List<String> lines, Properties state) {
    }

    public static String adminReply(java.util.List<String> lines, ServerSettings settings) {
        Properties p = new Properties();
        p.setProperty("protocol", String.valueOf(VERSION));
        for (int i = 0; i < lines.size(); i++) {
            p.setProperty("line." + i, lines.get(i));
        }
        settings.writeState(p, "state.");
        return write(p);
    }

    /** @return the reply, or {@code null} when the text is not one */
    public static AdminReply parseAdminReply(String text) {
        Properties p = read(text);
        if (p == null || DistanceConfig.parseDouble(p, "protocol", -1) < 1) {
            return null;
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; p.getProperty("line." + i) != null; i++) {
            lines.add(p.getProperty("line." + i));
        }
        Properties state = new Properties();
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith("state.")) {
                state.setProperty(key.substring("state.".length()), p.getProperty(key));
            }
        }
        return new AdminReply(lines, state);
    }

    /** @param states voice chat state per player UUID, closest first; only the first {@link #MAX_NEARBY} are sent */
    public static String nearby(Map<UUID, VoiceState> states) {
        return nearby(states, GroupInfo.NONE);
    }

    /** @param group the receiver's group, see {@link GroupInfo} */
    public static String nearby(Map<UUID, VoiceState> states, GroupInfo group) {
        Properties p = new Properties();
        p.setProperty("protocol", String.valueOf(VERSION));
        int n = 0;
        for (Map.Entry<UUID, VoiceState> e : states.entrySet()) {
            if (n++ >= MAX_NEARBY) {
                break;
            }
            p.setProperty(PLAYER_PREFIX + e.getKey(), e.getValue().getId());
            if (group.mates().contains(e.getKey())) {
                p.setProperty(MATE_PREFIX + e.getKey(), "1");
            } else if (group.isolated().contains(e.getKey())) {
                p.setProperty(ISOLATED_PREFIX + e.getKey(), "1");
            }
        }
        if (group.hasTotals()) {
            p.setProperty("group_hear", String.valueOf(group.groupHear()));
            p.setProperty("group_deaf", String.valueOf(group.groupDeaf()));
        }
        return write(p);
    }

    /** @return the group part of a {@code nearby} message; {@link GroupInfo#NONE} when it has none or is invalid */
    public static GroupInfo parseNearbyGroup(String text) {
        Properties p = read(text);
        if (p == null) {
            return GroupInfo.NONE;
        }
        Set<UUID> mates = new HashSet<>();
        Set<UUID> isolated = new HashSet<>();
        for (String key : p.stringPropertyNames()) {
            Set<UUID> target = key.startsWith(MATE_PREFIX) ? mates : key.startsWith(ISOLATED_PREFIX) ? isolated : null;
            if (target == null || target.size() >= MAX_NEARBY) {
                continue;
            }
            try {
                target.add(UUID.fromString(key.substring(key.indexOf('.') + 1)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        int hear = (int) DistanceConfig.parseDouble(p, "group_hear", -1);
        int deaf = (int) DistanceConfig.parseDouble(p, "group_deaf", -1);
        if (hear < 0 || deaf < 0) {
            hear = -1;
            deaf = -1;
        }
        return new GroupInfo(Set.copyOf(mates), Set.copyOf(isolated), hear, deaf);
    }

    /** @return the state per player, or {@code null} when the text is not a valid nearby message; unknown entries are skipped */
    public static Map<UUID, VoiceState> parseNearby(String text) {
        Properties p = read(text);
        if (p == null || DistanceConfig.parseDouble(p, "protocol", -1) < 1) {
            return null;
        }
        Map<UUID, VoiceState> states = new LinkedHashMap<>();
        for (String key : p.stringPropertyNames()) {
            if (!key.startsWith(PLAYER_PREFIX) || states.size() >= MAX_NEARBY) {
                continue;
            }
            VoiceState state = VoiceState.fromId(p.getProperty(key));
            if (state == null) {
                continue;
            }
            try {
                states.put(UUID.fromString(key.substring(PLAYER_PREFIX.length())), state);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Collections.unmodifiableMap(states);
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
