package com.kasper.vcdistance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
 *     <li>Echo, water and weather in the profile.</li>
 *     <li>Zones: worlds and WorldGuard regions with their own mode or preset.</li>
 * </ol>
 * All of it ends up in {@link #profile()}, which drives server walls and is sent to the addon.
 */
public final class ServerSettings {

    private static final String FILE_NAME = "vc-audio-distance-server.properties";
    private static final String PROFILE_PREFIX = "profile.";
    /**
     * 2: sections with comments, walls_strength and material.* at the top level, profile_preset;
     * 3: echo, water and weather; 4: zones and messages_language; 5: more materials;
     * 6: boxes and zone rules, game rules, the addon requirement, messages in every language.
     */
    private static final int SETTINGS_VERSION = 6;
    private static final String ZONE_PREFIX = "zone.";
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

    /** What happens to players who have Simple Voice Chat but not this addon (or an older one). */
    public enum RequireAddon {
        /** Nothing. */
        OFF("off"),
        /** One chat message per server start, with where to get the addon. */
        SUGGEST("suggest"),
        /** The message on every join. */
        WARN("warn"),
        /** They are disconnected with the message. */
        KICK("kick");

        private final String id;

        RequireAddon(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public static RequireAddon fromId(String id, RequireAddon fallback) {
            if (id != null) {
                for (RequireAddon r : values()) {
                    if (r.id.equalsIgnoreCase(id.trim())) {
                        return r;
                    }
                }
            }
            return fallback;
        }
    }

    public static final double DEFAULT_MEGAPHONE_MULTIPLIER = 2.5;
    public static final String DEFAULT_ADDON_URL = "https://github.com/Shamanalle/voicechat-audio-distance/releases";

    private final Path path;
    private final DistanceConfig profile;
    private volatile double sneakMultiplier = 1.0;
    private volatile boolean deadSilent;
    private volatile boolean spectatorsOnly;
    private volatile String megaphoneItem = "";
    private volatile double megaphoneMultiplier = DEFAULT_MEGAPHONE_MULTIPLIER;
    private volatile RequireAddon requireAddon = RequireAddon.OFF;
    private volatile String minAddonVersion = "";
    private volatile String addonUrl = DEFAULT_ADDON_URL;
    private volatile ProfileMode profileMode = ProfileMode.OFF;
    private volatile String profilePreset = CUSTOM_PRESET;
    private volatile boolean serverWalls = true;
    private volatile int maxStreams = DEFAULT_MAX_STREAMS;
    private volatile long loadedModified = Long.MIN_VALUE;
    private volatile Map<String, Zone> zones = Map.of();
    private volatile String messagesLanguage = "auto";

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
        // The admin's own texts sit next to the settings file (written on first start)
        Path parent = file.toAbsolutePath().getParent();
        ServerText.useFolder(parent == null ? null : parent.resolve(ServerText.FOLDER));
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
        String language = props.getProperty("messages_language", "auto").trim().toLowerCase(Locale.ROOT);
        messagesLanguage = language.isEmpty() || language.equals("auto") ? "auto" : ServerText.language(language);
        zones = readZones(props, file);
        sneakMultiplier = DistanceConfig.clamp(DistanceConfig.parseDouble(props, "sneak_range_multiplier", 1.0), 0.1, 1.0);
        deadSilent = DistanceConfig.parseBoolean(props, "dead_players_silent", false);
        spectatorsOnly = DistanceConfig.parseBoolean(props, "spectators_hear_only_spectators", false);
        megaphoneItem = itemId(props.getProperty("megaphone_item", ""));
        megaphoneMultiplier = DistanceConfig.clamp(DistanceConfig.parseDouble(props, "megaphone_multiplier", DEFAULT_MEGAPHONE_MULTIPLIER), 1.0, 10.0);
        requireAddon = RequireAddon.fromId(props.getProperty("require_addon"), RequireAddon.OFF);
        minAddonVersion = props.getProperty("min_addon_version", "").trim();
        String url = props.getProperty("addon_download_url", DEFAULT_ADDON_URL).trim();
        addonUrl = url.isEmpty() ? DEFAULT_ADDON_URL : url;
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
            // Fitted to the real voice range whenever the profile is sent (see profileIn)
            preset.apply(profile, AudioDistancePlugin.FALLBACK_DISTANCE);
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

        w.section("5. Zones", "5. Зоны")
                .comment("A world, a box or a WorldGuard region (Paper) can have its own sound; what a zone leaves out",
                        "comes from the sections above. Where zones overlap, the highest priority wins (then regions, then the",
                        "smallest box); a world's zone applies everywhere else in that world. Boxes are easiest to make in game:",
                        "/vcd zone pos1, /vcd zone pos2, /vcd zone create <name> (or /vcd zone create <name> <radius>).",
                        "  zone.<kind>.<name>.profile_mode      off / suggest / enforce",
                        "  zone.<kind>.<name>.profile_preset    vanilla / realistic / clear / stealth",
                        "  zone.<kind>.<name>.voice_range       voice range here, in blocks (also for players without the addon)",
                        "  zone.<kind>.<name>.whisper_range     whisper range here, in blocks",
                        "  zone.<kind>.<name>.range_multiplier  voice and whisper range times this: 2 = a stage, 0.4 = a library",
                        "  zone.<kind>.<name>.walls_strength    wall strength here, 0 - 1",
                        "  zone.<kind>.<name>.echo              auto (measured), off, or 0.1 - 1 for an echo this big everywhere here",
                        "  zone.<kind>.<name>.isolated          true: voices neither leave nor enter the zone",
                        "  zone.<kind>.<name>.enter_message     shown to players who enter",
                        "  zone.<kind>.<name>.priority          a whole number, default 0",
                        "  zone.box.<name>.world / from / to    the box: world, and two corners as x,y,z",
                        "<kind> is world (on Fabric the dimension: the_nether, the_end...), box or region (WorldGuard).",
                        "Мир, бокс или регион WorldGuard (Paper) могут звучать по-своему; чего в зоне нет, берётся из разделов выше.",
                        "Где зоны пересекаются, побеждает высший priority (при равном - регион, затем меньший бокс); зона мира",
                        "действует во всём остальном мире. Боксы проще всего создавать в игре: /vcd zone pos1, /vcd zone pos2,",
                        "/vcd zone create <имя> (или /vcd zone create <имя> <радиус>).",
                        "  profile_mode, profile_preset - как в разделе 3; voice_range, whisper_range - дальность в блоках",
                        "  (и для игроков без аддона); range_multiplier - дальность умножается: 2 - сцена, 0.4 - библиотека;",
                        "  walls_strength - сила стен 0 - 1; echo - auto (как измерено), off или 0.1 - 1: такое эхо везде в зоне;",
                        "  isolated - true: голоса не выходят из зоны и не заходят в неё; enter_message - сообщение при входе;",
                        "  priority - целое число, по умолчанию 0; zone.box.<имя>.world / from / to - мир и два угла бокса x,y,z.",
                        "<kind> - world (на Fabric измерение: the_nether, the_end...), box или region (WorldGuard).");
        for (Zone z : zones.values()) {
            String base = ZONE_PREFIX + z.kind() + "." + z.name() + ".";
            if (z.box() != null) {
                w.value(base + "world", z.box().world())
                        .value(base + "from", z.box().from())
                        .value(base + "to", z.box().to());
            }
            if (z.mode() != null) {
                w.value(base + "profile_mode", z.mode().getId());
            }
            if (z.preset() != null) {
                w.value(base + "profile_preset", z.preset());
            }
            Zone.Rules rules = z.rules();
            if (rules.voiceRange() != null) {
                w.value(base + "voice_range", rules.voiceRange());
            }
            if (rules.whisperRange() != null) {
                w.value(base + "whisper_range", rules.whisperRange());
            }
            if (rules.rangeMultiplier() != null) {
                w.value(base + "range_multiplier", rules.rangeMultiplier());
            }
            if (rules.wallsStrength() != null) {
                w.value(base + "walls_strength", rules.wallsStrength());
            }
            if (rules.echo() != null) {
                w.value(base + "echo", rules.echo() <= 0.0 ? "off" : ConfigWriter.number(rules.echo()));
            }
            if (rules.isolated()) {
                w.value(base + "isolated", true);
            }
            if (rules.enterMessage() != null) {
                w.value(base + "enter_message", rules.enterMessage());
            }
            if (z.priority() != 0) {
                w.value(base + "priority", z.priority());
            }
        }

        w.section("6. Game rules", "6. Правила игры")
                .comment("Voice range while sneaking, times this: 0.1 - 1. 1 = no change. Default 1.",
                        "Дальность голоса на корточках умножается на это: 0.1 - 1. 1 = без изменений. По умолчанию 1.")
                .value("sneak_range_multiplier", sneakMultiplier)
                .comment("true: dead players are not heard until they respawn (hardcore, mini-games). Default false.",
                        "true: мёртвых игроков не слышно, пока они не возродятся (хардкор, мини-игры). По умолчанию false.")
                .value("dead_players_silent", deadSilent)
                .comment("true: spectators are heard only by other spectators. Default false.",
                        "true: наблюдателей слышат только другие наблюдатели. По умолчанию false.")
                .value("spectators_hear_only_spectators", spectatorsOnly)
                .comment("An item that works as a megaphone while held, e.g. minecraft:goat_horn. Empty = no megaphone.",
                        "Предмет, который в руке работает как мегафон, например minecraft:goat_horn. Пусто = мегафона нет.")
                .value("megaphone_item", megaphoneItem)
                .comment("Voice range with the megaphone, times this: 1 - 10. Default 2.5.",
                        "Дальность голоса с мегафоном умножается на это: 1 - 10. По умолчанию 2.5.")
                .value("megaphone_multiplier", megaphoneMultiplier);

        w.section("7. Players without the addon: requirement", "7. Игроки без аддона: требование")
                .comment("For players who have Simple Voice Chat but not this addon (or a version below min_addon_version):",
                        "  off     - nothing (default);",
                        "  suggest - one chat message per server start, with where to get it;",
                        "  warn    - the message on every join;",
                        "  kick    - they are disconnected with the message.",
                        "Players without Simple Voice Chat are never affected.",
                        "Для игроков, у которых есть Simple Voice Chat, но нет этого аддона (или версия ниже min_addon_version):",
                        "  off - ничего (по умолчанию); suggest - одно сообщение в чате за запуск сервера, где скачать;",
                        "  warn - сообщение при каждом входе; kick - отключение с этим сообщением.",
                        "Игроков без Simple Voice Chat это не касается.")
                .value("require_addon", requireAddon.getId())
                .comment("Lowest addon version that counts, e.g. 1.8.0. Empty = any version.",
                        "Минимальная версия аддона, например 1.8.0. Пусто = любая.")
                .value("min_addon_version", minAddonVersion)
                .comment("Where players get the addon (shown in the message).", "Где игроки берут аддон (показывается в сообщении).")
                .value("addon_download_url", addonUrl);

        w.section("8. Messages", "8. Сообщения")
                .comment("Language of /vcd replies and of messages to players: auto (each player's own game language),",
                        "or en_us, ru_ru, uk_ua, de_de, es_es, pt_br, zh_cn. Default auto.",
                        "Язык ответов /vcd и сообщений игрокам: auto (язык игры самого игрока)",
                        "или en_us, ru_ru, uk_ua, de_de, es_es, pt_br, zh_cn. По умолчанию auto.")
                .value("messages_language", messagesLanguage);
        w.save(getPath());
        // Our own write is not an edit to pick up again
        loadedModified = lastModified(getPath());
    }

    /** Zones by {@link Zone#key()}. */
    public Map<String, Zone> zones() {
        return zones;
    }

    /** Language of command replies and notices: "auto" (the player's own) or a language file name. */
    public String getMessagesLanguage() {
        return messagesLanguage;
    }

    /** The language to answer a player in: theirs when set to auto, otherwise the configured one. */
    public String languageFor(String playerLanguage) {
        return "auto".equals(messagesLanguage) ? ServerText.language(playerLanguage) : messagesLanguage;
    }

    /** The zone a player is in, or {@code null}. */
    public Zone zoneOf(ServerPlayers.Info player) {
        if (player == null || zones.isEmpty()) {
            return null;
        }
        return Zone.resolve(zones, player.world(), player.regions(), player.x(), player.y(), player.z());
    }

    /** Adds or replaces a zone; call {@link #save()} to keep it. */
    public synchronized void putZone(Zone zone) {
        Map<String, Zone> copy = new LinkedHashMap<>(zones);
        copy.put(zone.key(), zone);
        zones = Collections.unmodifiableMap(copy);
    }

    /** @return {@code false} when there was no such zone */
    public synchronized boolean removeZone(String key) {
        if (!zones.containsKey(key)) {
            return false;
        }
        Map<String, Zone> copy = new LinkedHashMap<>(zones);
        copy.remove(key);
        zones = Collections.unmodifiableMap(copy);
        return true;
    }

    /** A zone by name, whatever its kind (boxes first), or {@code null}. */
    public Zone findZone(String name) {
        String n = Zone.normalize(name);
        for (String kind : new String[]{Zone.BOX, Zone.REGION, Zone.WORLD}) {
            Zone z = zones.get(kind + ":" + n);
            if (z != null) {
                return z;
            }
        }
        return null;
    }

    /** Whether any voice rule is on, so voice packets need looking at. */
    public boolean hasVoiceRules() {
        if (sneakMultiplier < 1.0 || deadSilent || spectatorsOnly || !megaphoneItem.isEmpty()) {
            return true;
        }
        for (Zone z : zones.values()) {
            if (z.rules().changesRange()) {
                return true;
            }
        }
        return false;
    }

    public double getSneakMultiplier() {
        return sneakMultiplier;
    }

    public void setSneakMultiplier(double m) {
        sneakMultiplier = DistanceConfig.clamp(m, 0.1, 1.0);
    }

    public boolean isDeadSilent() {
        return deadSilent;
    }

    public void setDeadSilent(boolean on) {
        deadSilent = on;
    }

    public boolean isSpectatorsOnly() {
        return spectatorsOnly;
    }

    public void setSpectatorsOnly(boolean on) {
        spectatorsOnly = on;
    }

    /** Megaphone item id ("minecraft:goat_horn"), or "" when there is none. */
    public String getMegaphoneItem() {
        return megaphoneItem;
    }

    public void setMegaphoneItem(String id) {
        megaphoneItem = itemId(id);
    }

    public double getMegaphoneMultiplier() {
        return megaphoneMultiplier;
    }

    public void setMegaphoneMultiplier(double m) {
        megaphoneMultiplier = DistanceConfig.clamp(m, 1.0, 10.0);
    }

    public RequireAddon getRequireAddon() {
        return requireAddon;
    }

    public void setRequireAddon(RequireAddon mode) {
        requireAddon = mode == null ? RequireAddon.OFF : mode;
    }

    /** Lowest addon version that counts, or "" for any. */
    public String getMinAddonVersion() {
        return minAddonVersion;
    }

    public void setMinAddonVersion(String version) {
        minAddonVersion = version == null ? "" : version.trim();
    }

    public String getAddonUrl() {
        return addonUrl;
    }

    /** How the profile is offered in a zone ({@code null}: the main profile). */
    public ProfileMode modeIn(Zone zone) {
        return zone != null && zone.mode() != null ? zone.mode() : profileMode;
    }

    /**
     * The profile in a zone: the main profile with the zone's preset on top (walls stay as in
     * section 1). Presets are fitted to {@code voiceRange}, the server's voice range in blocks.
     */
    public DistanceConfig profileIn(Zone zone, double voiceRange) {
        Preset preset = zone != null && zone.preset() != null ? presetByName(zone.preset()) : presetByName(profilePreset);
        Zone.Rules rules = zone == null ? Zone.Rules.NONE : zone.rules();
        if (preset == null && rules.wallsStrength() == null && rules.echo() == null) {
            return profile;
        }
        DistanceConfig c = profile.copy();
        boolean walls = c.isOcclusionEnabled();
        double strength = c.getOcclusionStrength();
        if (preset != null) {
            preset.apply(c, voiceRange);
        }
        c.setOcclusionEnabled(walls);
        c.setOcclusionStrength(strength);
        if (rules.wallsStrength() != null) {
            c.setOcclusionEnabled(rules.wallsStrength() > 0.0);
            c.setOcclusionStrength(rules.wallsStrength());
        }
        if (rules.echo() != null) {
            // An echo the zone sets is always on; the client uses its size instead of measuring the room
            c.setReverbEnabled(rules.echo() > 0.0);
        }
        return c;
    }

    /**
     * The settings the Server tab shows, under {@code prefix}: the main ones by their file names, and
     * each zone as {@code zone.<n>} = kind|name|mode|preset|voice|whisper|multiplier|walls|echo|isolated|priority
     * ("-" for unset).
     */
    public void writeState(Properties p, String prefix) {
        p.setProperty(prefix + "profile_mode", profileMode.getId());
        p.setProperty(prefix + "profile_preset", profilePreset);
        p.setProperty(prefix + "walls_strength", DistanceConfig.format(profile.isOcclusionEnabled() ? profile.getOcclusionStrength() : 0.0));
        p.setProperty(prefix + "server_walls", String.valueOf(serverWalls));
        p.setProperty(prefix + "sneak_range_multiplier", DistanceConfig.format(sneakMultiplier));
        p.setProperty(prefix + "dead_players_silent", String.valueOf(deadSilent));
        p.setProperty(prefix + "spectators_hear_only_spectators", String.valueOf(spectatorsOnly));
        p.setProperty(prefix + "megaphone_item", megaphoneItem);
        p.setProperty(prefix + "megaphone_multiplier", DistanceConfig.format(megaphoneMultiplier));
        p.setProperty(prefix + "require_addon", requireAddon.getId());
        p.setProperty(prefix + "min_addon_version", minAddonVersion);
        int n = 0;
        for (Zone z : zones.values()) {
            Zone.Rules r = z.rules();
            p.setProperty(prefix + "zone." + n++, String.join("|", z.kind(), z.name(),
                    z.mode() == null ? "-" : z.mode().getId(), z.preset() == null ? "-" : z.preset(),
                    num(r.voiceRange()), num(r.whisperRange()), num(r.rangeMultiplier()), num(r.wallsStrength()),
                    r.echo() == null ? "-" : r.echo() <= 0.0 ? "off" : ConfigWriter.number(r.echo()),
                    String.valueOf(r.isolated()), String.valueOf(z.priority())));
        }
    }

    private static String num(Double v) {
        return v == null ? "-" : ConfigWriter.number(v);
    }

    /** Wall strength for a listener in {@code zone} (0 = walls off), for the server's own muffling. */
    public double wallsStrengthIn(Zone zone) {
        if (zone != null && zone.rules().wallsStrength() != null) {
            return zone.rules().wallsStrength();
        }
        return profile.isOcclusionEnabled() ? profile.getOcclusionStrength() : 0.0;
    }

    private static Map<String, Zone> readZones(Properties props, Path file) {
        // kind:name -> field -> value
        Map<String, Map<String, String>> parts = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames().stream().sorted().toList()) {
            if (!key.startsWith(ZONE_PREFIX)) {
                continue;
            }
            String rest = key.substring(ZONE_PREFIX.length());
            int kindEnd = rest.indexOf('.');
            int fieldStart = rest.lastIndexOf('.');
            if (kindEnd <= 0 || fieldStart <= kindEnd + 1) {
                DistanceConfig.LOGGER.warn("Ignoring '{}' in {}: expected zone.<world|box|region>.<name>.<setting>", key, file);
                continue;
            }
            String kind = rest.substring(0, kindEnd).toLowerCase(Locale.ROOT);
            String name = Zone.normalize(rest.substring(kindEnd + 1, fieldStart));
            String field = rest.substring(fieldStart + 1).toLowerCase(Locale.ROOT);
            if (!kind.equals(Zone.WORLD) && !kind.equals(Zone.REGION) && !kind.equals(Zone.BOX)) {
                DistanceConfig.LOGGER.warn("Ignoring '{}' in {}: a zone is a world, a box or a region", key, file);
                continue;
            }
            parts.computeIfAbsent(kind + ":" + name, k -> new LinkedHashMap<>()).put(field, props.getProperty(key).trim());
        }
        Map<String, Zone> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> e : parts.entrySet()) {
            String kind = e.getKey().substring(0, e.getKey().indexOf(':'));
            String name = e.getKey().substring(e.getKey().indexOf(':') + 1);
            Zone z = zoneFrom(kind, name, e.getValue(), file);
            if (z != null) {
                result.put(z.key(), z);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /** Builds a zone from its settings; {@code null} (with a warning) when it cannot be used. */
    static Zone zoneFrom(String kind, String name, Map<String, String> fields, Path file) {
        ProfileMode mode = null;
        String preset = null;
        Double voice = null;
        Double whisper = null;
        Double multiplier = null;
        Double walls = null;
        Double echo = null;
        boolean isolated = false;
        String message = null;
        int priority = 0;
        String world = null;
        int[] from = null;
        int[] to = null;
        for (Map.Entry<String, String> f : fields.entrySet()) {
            String v = f.getValue();
            switch (f.getKey()) {
                case "profile_mode" -> {
                    mode = ProfileMode.fromId(v, null);
                    if (mode == null) {
                        DistanceConfig.LOGGER.warn("Unknown profile_mode '{}' for zone {} in {}", v, name, file);
                    }
                }
                case "profile_preset" -> {
                    Preset p = presetByName(v);
                    if (p == null) {
                        DistanceConfig.LOGGER.warn("Unknown preset '{}' for zone {} in {}", v, name, file);
                    } else {
                        preset = nameOf(p);
                    }
                }
                case "voice_range" -> voice = positive(v, 1.0, 1000.0);
                case "whisper_range" -> whisper = positive(v, 1.0, 1000.0);
                case "range_multiplier" -> multiplier = positive(v, 0.05, 10.0);
                case "walls_strength" -> walls = positive(v, 0.0, 1.0);
                case "echo" -> echo = parseEcho(v);
                case "isolated" -> isolated = Boolean.parseBoolean(v);
                case "enter_message" -> message = v.isEmpty() ? null : v;
                case "priority" -> {
                    try {
                        priority = Integer.parseInt(v);
                    } catch (NumberFormatException ex) {
                        DistanceConfig.LOGGER.warn("Zone {}: priority '{}' is not a whole number", name, v);
                    }
                }
                case "world" -> world = v;
                case "from" -> from = corner(v);
                case "to" -> to = corner(v);
                default -> DistanceConfig.LOGGER.warn("Ignoring zone setting '{}' for zone {} in {}", f.getKey(), name, file);
            }
        }
        Zone.Box box = null;
        if (kind.equals(Zone.BOX)) {
            if (world == null || world.isEmpty() || from == null || to == null) {
                DistanceConfig.LOGGER.warn("Box zone {} in {} needs world, from and to (x,y,z); ignored", name, file);
                return null;
            }
            box = new Zone.Box(world, from[0], from[1], from[2], to[0], to[1], to[2]);
        }
        Zone.Rules rules = new Zone.Rules(voice, whisper, multiplier, walls, echo, isolated, message);
        if (box == null && mode == null && preset == null && rules.isEmpty()) {
            return null;
        }
        return new Zone(kind, name, mode, preset, rules, box, priority);
    }

    private static Double positive(String v, double min, double max) {
        try {
            double d = Double.parseDouble(v.trim());
            return Double.isFinite(d) ? DistanceConfig.clamp(d, min, max) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "auto" (or empty) = null, "off" = 0, a number = that echo size 0.1 - 1. */
    static Double parseEcho(String v) {
        String t = v.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty() || t.equals("auto")) {
            return null;
        }
        if (t.equals("off") || t.equals("false") || t.equals("0")) {
            return 0.0;
        }
        Double d = positive(t, 0.1, 1.0);
        return d;
    }

    private static int[] corner(String v) {
        String[] p = v.split("[,\\s]+");
        if (p.length != 3) {
            return null;
        }
        try {
            return new int[]{(int) Math.floor(Double.parseDouble(p[0])), (int) Math.floor(Double.parseDouble(p[1])),
                    (int) Math.floor(Double.parseDouble(p[2]))};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "goat_horn" and "minecraft:goat_horn" are the same item. */
    static String itemId(String id) {
        String t = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty() || t.equals("none") || t.equals("off")) {
            return "";
        }
        return t.indexOf(':') >= 0 ? t : "minecraft:" + t;
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

    static String nameOf(Preset preset) {
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

    /** Walls for every player, 0 - 1 (0 = off). */
    public void setWallsStrength(double strength) {
        double s = DistanceConfig.clamp(strength, DistanceConfig.STRENGTH_MIN, DistanceConfig.STRENGTH_MAX);
        profile.setOcclusionEnabled(s > 0.0);
        profile.setOcclusionStrength(s);
    }

    /** Changes the mode in memory; call {@link #save()} to keep it. */
    public void setProfileMode(ProfileMode mode) {
        this.profileMode = mode == null ? ProfileMode.OFF : mode;
    }

    /** Applies a preset by name ({@link #CUSTOM_PRESET} keeps the current values); call {@link #save()} to keep it. */
    public void setProfilePreset(String name) {
        Preset preset = presetByName(name);
        if (preset == null) {
            profilePreset = CUSTOM_PRESET;
            return;
        }
        boolean walls = profile.isOcclusionEnabled();
        double strength = profile.getOcclusionStrength();
        preset.apply(profile, AudioDistancePlugin.FALLBACK_DISTANCE);
        profile.setOcclusionEnabled(walls);
        profile.setOcclusionStrength(strength);
        profilePreset = nameOf(preset);
    }

    /**
     * Takes the whole profile (curve, walls, materials, effects) from a {@link ProfileCode}; the
     * preset becomes {@link #CUSTOM_PRESET}. Call {@link #save()} to keep it.
     *
     * @return {@code false} when the code is not valid (nothing changes)
     */
    public boolean importProfile(String code) {
        DistanceConfig imported = profile.copy();
        if (!ProfileCode.decode(code, imported)) {
            return false;
        }
        profile.copyFrom(imported);
        profilePreset = CUSTOM_PRESET;
        return true;
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
