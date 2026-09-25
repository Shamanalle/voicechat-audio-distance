package com.kasper.vcdistance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Server-side settings, stored in {@code config/vc-audio-distance-server.properties} (Fabric) or
 * {@code plugins/VoicechatAudioDistance/} (Bukkit). The file is re-read automatically when it
 * changes on disk, and is written for people: three sections with a comment on every key.
 * <ol>
 *     <li>Walls: strength and material weights, the same for every player.</li>
 *     <li>Players without the addon: whether the server muffles walls for them, and its CPU limit.</li>
 *     <li>Players with the addon: how the profile is offered, and the profile itself, either a
 *     named preset or custom {@code profile.*} values.</li>
 * </ol>
 * All of it ends up in {@link #profile()}, which drives server walls and is sent to the addon.
 */
public final class ServerSettings {

    private static final String FILE_NAME = "vc-audio-distance-server.properties";
    private static final String PROFILE_PREFIX = "profile.";
    /** 2: sections with comments, walls_strength and material.* at the top level, profile_preset. */
    private static final int SETTINGS_VERSION = 3;
    public static final String CUSTOM_PRESET = "custom";

    public static final int DEFAULT_MAX_STREAMS = 24;
    public static final int MAX_STREAMS_LIMIT = 512;

    /** How the server profile is offered to players who have the addon. */
    public enum ProfileMode {
        /** Players keep their own settings. */
        OFF("off"),
        /** Players see the server's profile and can apply it with one click. */
        SUGGEST("suggest"),
        /** Players hear the server's profile while connected; their own settings return when they leave. */
        ENFORCE("enforce");

        private final String id;

        ProfileMode(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public static ProfileMode fromId(String id, ProfileMode fallback) {
            if (id != null) {
                for (ProfileMode m : values()) {
                    if (m.id.equalsIgnoreCase(id.trim())) {
                        return m;
                    }
                }
            }
            return fallback;
        }
    }

    private final Path path;
    private final DistanceConfig profile;
    private volatile ProfileMode profileMode = ProfileMode.OFF;
    private volatile String profilePreset = CUSTOM_PRESET;
    private volatile boolean serverWalls = true;
    private volatile int maxStreams = DEFAULT_MAX_STREAMS;
    private volatile long loadedModified = Long.MIN_VALUE;

    public ServerSettings() {
        this(null);
    }

    /**
     * @param path file to use, or {@code null} for the loader's config directory
     */
    public ServerSettings(Path path) {
        this.path = path;
        this.profile = new DistanceConfig(Path.of(FILE_NAME));
    }

    public Path getPath() {
        return path != null ? path : ModEnvironment.configDir().resolve(FILE_NAME);
    }

    /** The server's sound profile. Its wall settings also drive server-side muffling. */
    public DistanceConfig profile() {
        return profile;
    }

    public ProfileMode getProfileMode() {
        return profileMode;
    }

    /** Name of the preset the profile is based on, or {@link #CUSTOM_PRESET}. */
    public String getProfilePreset() {
        return profilePreset;
    }

    /** Server-side wall muffling for players without the addon. */
    public boolean isServerWalls() {
        return serverWalls;
    }

    /** Maximum number of voices re-encoded at the same time for server-side muffling. */
    public int getMaxStreams() {
        return maxStreams;
    }

    public synchronized void load() {
        Path file = getPath();
        if (!Files.exists(file)) {
            save();
            loadedModified = lastModified(file);
            return;
        }
        Properties props;
        try {
            props = ConfigWriter.load(file);
        } catch (IOException e) {
            DistanceConfig.LOGGER.error("Failed to read {}, keeping previous server settings: {}", file, e.getMessage());
            return;
        }
        profileMode = ProfileMode.fromId(props.getProperty("profile_mode"), ProfileMode.OFF);
        serverWalls = DistanceConfig.parseBoolean(props, "server_walls", true);
        maxStreams = (int) DistanceConfig.clamp(DistanceConfig.parseDouble(props, "server_walls_max_streams", DEFAULT_MAX_STREAMS), 0, MAX_STREAMS_LIMIT);

        // Profile: custom values, or a preset on top of them
        profile.readFrom(props, PROFILE_PREFIX);
        String presetName = props.getProperty("profile_preset", CUSTOM_PRESET).trim().toLowerCase(Locale.ROOT);
        Preset preset = presetByName(presetName);
        if (preset == null && !CUSTOM_PRESET.equals(presetName)) {
            DistanceConfig.LOGGER.warn("Unknown profile_preset '{}' in {}, using the custom profile values", presetName, file);
            presetName = CUSTOM_PRESET;
        }
        if (preset != null) {
            preset.apply(profile);
            presetName = nameOf(preset);
        }
        profilePreset = presetName;

        // Walls are shared by everyone. Files from 1.2.0 kept them under profile.*, which readFrom already read.
        double legacyStrength = profile.isOcclusionEnabled() ? profile.getOcclusionStrength() : 0.0;
        double strength = DistanceConfig.clamp(DistanceConfig.parseDouble(props, "walls_strength", legacyStrength),
                DistanceConfig.STRENGTH_MIN, DistanceConfig.STRENGTH_MAX);
        profile.setOcclusionEnabled(strength > 0.0);
        profile.setOcclusionStrength(strength);
        for (AcousticMaterial m : AcousticMaterial.values()) {
            profile.setMaterialWeight(m, DistanceConfig.parseDouble(props, "material." + m.getId(), profile.getMaterialWeight(m)));
        }

        if (DistanceConfig.parseDouble(props, "settings_version", 1) < SETTINGS_VERSION) {
            save();
            DistanceConfig.LOGGER.info("{} was rewritten in the new, commented format; your values are kept", file);
        }
        loadedModified = lastModified(file);
        DistanceConfig.LOGGER.info("Server settings loaded: walls {}, server walls {} (max {} voices), profile {} ({})",
                ConfigWriter.number(strength), serverWalls, maxStreams, profileMode.getId(), profilePreset);
    }

    public synchronized void save() {
        ConfigWriter w = new ConfigWriter()
                .title("VoiceChat Audio Distance - server settings",
                        "Changes are applied within 2 seconds, no restart needed.",
                        "The voice and whisper range are set in Simple Voice Chat: max_voice_distance, whisper_distance.",
                        "",
                        "VoiceChat Audio Distance - настройки сервера",
                        "Изменения применяются в течение 2 секунд, перезапуск не нужен.",
                        "Дальность голоса и шёпота задаётся в Simple Voice Chat: max_voice_distance, whisper_distance.")
                .comment("Format version, do not change. / Версия формата, не меняйте.")
                .value("settings_version", SETTINGS_VERSION);

        w.section("1. Walls, for every player", "1. Стены, для всех игроков")
                .comment("How strongly walls muffle voices, 0 - 1. 0 = walls off. Default 0.6.",
                        "Насколько сильно стены глушат голоса, 0 - 1. 0 = стены выключены. По умолчанию 0.6.")
                .value("walls_strength", profile.isOcclusionEnabled() ? profile.getOcclusionStrength() : 0.0);
        profile.writeMaterials(w, "");

        w.section("2. Players without the addon", "2. Игроки без аддона")
                .comment("true: the server muffles voices through walls for players with plain Simple Voice Chat.",
                        "false: they hear voices without walls. Default true.",
                        "true: сервер глушит голоса за стенами для игроков с обычным Simple Voice Chat.",
                        "false: они слышат голоса без стен. По умолчанию true.")
                .value("server_walls", serverWalls)
                .comment("Most voices the server muffles at the same time, 0 - 512 (CPU limit).",
                        "Voices above the limit are heard without walls. Default 24.",
                        "Сколько голосов сервер глушит одновременно, 0 - 512 (ограничение нагрузки на процессор).",
                        "Голоса сверх лимита слышно без стен. По умолчанию 24.")
                .value("server_walls_max_streams", maxStreams);

        w.section("3. Players with the addon", "3. Игроки с аддоном")
                .comment("What they get from the server:",
                        "  off     - they keep their own settings;",
                        "  suggest - a chat message and an \"Apply server profile\" button;",
                        "  enforce - the profile below is used while they play here (fair play for PvP and events).",
                        "Default off.",
                        "Что они получают от сервера:",
                        "  off     - остаются со своими настройками;",
                        "  suggest - сообщение в чате и кнопка «Применить профиль сервера»;",
                        "  enforce - профиль ниже действует, пока они играют здесь (честная игра в PvP и на ивентах).",
                        "По умолчанию off.")
                .value("profile_mode", profileMode.getId())
                .comment("The server's sound profile:",
                        "  vanilla   - like plain Simple Voice Chat;",
                        "  realistic - natural falloff;",
                        "  clear     - everyone stays understandable (events, meetings);",
                        "  stealth   - short range, fades fast (hide-and-seek, horror);",
                        "  custom    - the profile.* values below.",
                        "Walls always come from section 1. Default custom.",
                        "Профиль звука сервера:",
                        "  vanilla   - как обычный Simple Voice Chat;",
                        "  realistic - естественный спад;",
                        "  clear     - всех хорошо слышно (ивенты, собрания);",
                        "  stealth   - короткая дальность, быстрый спад (прятки, хоррор);",
                        "  custom    - значения profile.* ниже.",
                        "Стены всегда берутся из раздела 1. По умолчанию custom.")
                .value("profile_preset", profilePreset)
                .comment("Custom profile: used only when profile_preset=custom.",
                        "Свой профиль: работает, только когда profile_preset=custom.");
        profile.writeCurve(w, PROFILE_PREFIX);

        w.section("4. Echo, water and weather in the profile", "4. Эхо, вода и погода в профиле")
                .comment("Part of the profile above whatever profile_preset says; they only matter when it is suggested or enforced.",
                        "Входят в профиль выше при любом profile_preset; действуют, только когда он рекомендован или закреплён.");
        profile.writeEffects(w, PROFILE_PREFIX);
        w.save(getPath());
    }

    /** A preset by the name used in the settings file (or its internal id), or {@code null}. */
    static Preset presetByName(String name) {
        if (name == null) {
            return null;
        }
        switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "vanilla":
            case "default":
                return Preset.VANILLA;
            case "realistic":
                return Preset.REALISTIC;
            case "clear":
            case "high_audibility":
                return Preset.CLEAR;
            case "stealth":
            case "atmospheric":
                return Preset.ATMOSPHERIC;
            default:
                return null;
        }
    }

    private static String nameOf(Preset preset) {
        switch (preset) {
            case VANILLA:
                return "vanilla";
            case CLEAR:
                return "clear";
            case ATMOSPHERIC:
                return "stealth";
            default:
                return preset.getId();
        }
    }

    /**
     * Re-reads the file if it changed on disk.
     *
     * @return {@code true} when new settings were loaded
     */
    public synchronized boolean reloadIfChanged() {
        long modified = lastModified(getPath());
        if (modified == loadedModified) {
            return false;
        }
        load();
        return true;
    }

    /** For tests and tools: changes the mode in memory. */
    public void setProfileMode(ProfileMode mode) {
        this.profileMode = mode == null ? ProfileMode.OFF : mode;
    }

    /** For tests and tools: applies a preset by name ({@link #CUSTOM_PRESET} keeps the current values). */
    public void setProfilePreset(String name) {
        Preset preset = presetByName(name);
        if (preset == null) {
            profilePreset = CUSTOM_PRESET;
            return;
        }
        boolean walls = profile.isOcclusionEnabled();
        double strength = profile.getOcclusionStrength();
        preset.apply(profile);
        profile.setOcclusionEnabled(walls);
        profile.setOcclusionStrength(strength);
        profilePreset = nameOf(preset);
    }

    public void setServerWalls(boolean enabled) {
        this.serverWalls = enabled;
    }

    public void setMaxStreams(int maxStreams) {
        this.maxStreams = Math.max(0, Math.min(MAX_STREAMS_LIMIT, maxStreams));
    }

    private static long lastModified(Path file) {
        try {
            return Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : Long.MIN_VALUE + 1;
        } catch (IOException e) {
            return Long.MIN_VALUE + 1;
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "ServerSettings[mode=%s, preset=%s, walls=%s, max=%d]", profileMode.getId(), profilePreset, serverWalls, maxStreams);
    }
}
