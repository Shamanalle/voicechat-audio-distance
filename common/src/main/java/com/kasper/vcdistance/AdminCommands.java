package com.kasper.vcdistance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code /vcd} command for server admins, the same on Fabric, NeoForge and Paper (and behind the
 * Server tab): the platform only passes the typed text and prints the reply lines. Changes are saved
 * to the settings file and sent to the players who have the addon right away. Replies come in the
 * admin's own game language, or the one set in the settings file.
 * <pre>
 * /vcd status                                  what the addon is doing now
 * /vcd reload                                  re-read the settings file
 * /vcd profile off|suggest|enforce             how the profile is offered
 * /vcd preset vanilla|realistic|clear|stealth|custom
 * /vcd preset export | import &lt;code&gt;             the profile as a code players can paste
 * /vcd walls 0-100|off                         wall strength for everyone, in %
 * /vcd serverwalls on|off                      walls for players without the addon
 * /vcd lock all|none|curve,walls,...           what players cannot change while the profile is enforced
 * /vcd monitor on|off                          monitor, radar and nearby players in the HUD
 * /vcd zones                                   every zone
 * /vcd zone pos1|pos2 | create &lt;name&gt; [radius] | set &lt;name&gt; &lt;setting&gt; &lt;value&gt; | delete &lt;name&gt; | info
 * /vcd rule sneak|dead|spectators|megaphone|megaphone_range &lt;value&gt;
 * /vcd require off|suggest|warn|kick [min version]
 * /vcd debug &lt;player&gt;                          what a player hears, and why not
 * </pre>
 */
public final class AdminCommands {

    public static final String NAME = "vcd";
    static final String[] SUBCOMMANDS = {"status", "reload", "profile", "preset", "walls", "serverwalls", "lock", "monitor", "zones", "zone",
            "rule", "require", "debug", "help"};
    static final String[] MODES = {"off", "suggest", "enforce"};
    static final String[] PRESETS = {"vanilla", "realistic", "clear", "stealth", "custom", "export", "import"};
    static final String[] ZONE_ACTIONS = {"pos1", "pos2", "create", "set", "delete", "info", "list"};
    static final String[] ZONE_SETTINGS = {"mode", "preset", "voice_range", "whisper_range", "range_multiplier", "walls",
            "echo", "isolated", "message", "priority"};
    static final String[] RULES = {"sneak", "dead", "spectators", "megaphone", "megaphone_range"};
    static final String[] REQUIRE = {"off", "suggest", "warn", "kick"};
    /** Largest box {@code zone create <name> <radius>} makes around the admin. */
    static final int MAX_RADIUS = 256;

    /** What the platform provides to the command. */
    public interface Context {

        /** "Fabric", "NeoForge" or "Paper", for the status line. */
        String platform();

        int onlinePlayers();

        int addonPlayers();

        /** Sends every player with the addon their (zone's) profile again. */
        void resendProfiles();

        /** Drops caches that depend on the settings (block acoustics). */
        default void afterSettingsChange() {
        }

        /** The player running the command, or {@code null} for the console. */
        default UUID sender() {
            return null;
        }

        /** Online players as the voice rules see them. */
        default ServerPlayers players() {
            return AudioDistancePlugin.PLAYERS;
        }
    }

    /** Corners picked with {@code zone pos1/pos2}, per admin, until the server stops. */
    private static final Map<UUID, int[]> POS1 = new ConcurrentHashMap<>();
    private static final Map<UUID, int[]> POS2 = new ConcurrentHashMap<>();
    private static final Map<UUID, String> POS_WORLD = new ConcurrentHashMap<>();

    private AdminCommands() {
    }

    /**
     * Runs a command.
     *
     * @param input everything typed after {@code /vcd}, possibly empty
     * @return the reply, one line per entry
     */
    public static List<String> run(String input, ServerSettings settings, Context ctx) {
        ServerPlayers.Info me = ctx.sender() == null ? null : ctx.players().get(ctx.sender());
        Messages m = new Messages(settings.languageFor(me == null ? "" : me.language()));
        String[] args = input == null || input.isBlank() ? new String[0] : input.trim().split("\\s+");
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        switch (sub) {
            case "status" -> status(settings, ctx, m, out);
            case "reload" -> {
                settings.load();
                ctx.afterSettingsChange();
                ctx.resendProfiles();
                out.add(m.get("reloaded", settings.getPath().toString()));
            }
            case "profile" -> {
                ServerSettings.ProfileMode mode = args.length > 1 ? ServerSettings.ProfileMode.fromId(args[1], null) : null;
                if (mode == null) {
                    out.add(m.get("usage", "/vcd profile off|suggest|enforce"));
                    break;
                }
                settings.setProfileMode(mode);
                saved(settings, ctx, out, m.get("profile_set", mode.getId()));
            }
            case "preset" -> preset(args, settings, ctx, m, out);
            case "walls" -> {
                Double strength = args.length > 1 ? parsePercent(args[1]) : null;
                if (strength == null) {
                    out.add(m.get("usage", "/vcd walls 0-100|off"));
                    break;
                }
                settings.setWallsStrength(strength);
                saved(settings, ctx, out, strength > 0.0 ? m.get("walls_set", pct(strength)) : m.get("walls_off"));
            }
            case "serverwalls" -> {
                Boolean on = args.length > 1 ? parseOnOff(args[1]) : null;
                if (on == null) {
                    out.add(m.get("usage", "/vcd serverwalls on|off"));
                    break;
                }
                settings.setServerWalls(on);
                saved(settings, ctx, out, m.get(on ? "serverwalls_on" : "serverwalls_off"));
            }
            case "lock" -> {
                java.util.Set<DistanceConfig.Part> parts = args.length > 1
                        ? DistanceConfig.Part.parseSet(String.join(",", java.util.Arrays.copyOfRange(args, 1, args.length))) : null;
                if (parts == null) {
                    out.add(m.get("usage", "/vcd lock all|none|curve,walls,materials,effects"));
                    break;
                }
                settings.setLockedParts(parts);
                saved(settings, ctx, out, m.get("lock_set", DistanceConfig.Part.format(parts)));
            }
            case "monitor" -> {
                Boolean on = args.length > 1 ? parseOnOff(args[1]) : null;
                if (on == null) {
                    out.add(m.get("usage", "/vcd monitor on|off"));
                    break;
                }
                settings.setMonitorAllowed(on);
                saved(settings, ctx, out, m.get(on ? "monitor_on" : "monitor_off"));
            }
            case "zones" -> zones(settings, m, out);
            case "zone" -> zone(args, settings, ctx, me, m, out);
            case "rule" -> rule(args, settings, ctx, m, out);
            case "require" -> require(args, settings, ctx, m, out);
            case "debug" -> debug(args, settings, ctx, m, out);
            default -> help(m, out);
        }
        return out;
    }

    /** Completions for the word being typed. */
    public static List<String> suggest(String input) {
        String text = input == null ? "" : input;
        String[] args = text.stripLeading().split("\\s+", -1);
        List<String> out = new ArrayList<>();
        if (args.length <= 1) {
            addMatching(out, SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        } else if (args.length == 2) {
            String[] options = switch (args[0].toLowerCase(Locale.ROOT)) {
                case "profile" -> MODES;
                case "preset" -> PRESETS;
                case "walls" -> new String[]{"off", "30", "60", "85", "100"};
                case "serverwalls", "monitor" -> new String[]{"on", "off"};
                case "lock" -> new String[]{"all", "none", "curve", "walls", "materials", "effects", "curve,walls"};
                case "zone" -> ZONE_ACTIONS;
                case "rule" -> RULES;
                case "require" -> REQUIRE;
                default -> new String[0];
            };
            addMatching(out, options, args[1]);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("zone") && args[1].equalsIgnoreCase("set")) {
            addMatching(out, ZONE_SETTINGS, args[3]);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("rule")) {
            String[] options = switch (args[1].toLowerCase(Locale.ROOT)) {
                case "sneak" -> new String[]{"1", "0.5", "0.3"};
                case "dead", "spectators" -> new String[]{"on", "off"};
                case "megaphone" -> new String[]{"off", "minecraft:goat_horn"};
                case "megaphone_range" -> new String[]{"2", "2.5", "4"};
                default -> new String[0];
            };
            addMatching(out, options, args[2]);
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Subcommands
    // -------------------------------------------------------------------------

    private static void status(ServerSettings settings, Context ctx, Messages m, List<String> out) {
        out.add(m.get("status.title", BuildInfo.version(), ctx.platform()));
        double voice = AudioDistancePlugin.serverVoiceDistance();
        out.add(voice > 0.0
                ? m.get("status.svc", fmt(voice), fmt(AudioDistancePlugin.serverWhisperDistance()))
                : m.get("status.svc_off"));
        DistanceConfig p = settings.profile();
        String walls = p.isOcclusionEnabled() ? pct(p.getOcclusionStrength()) : m.get("off");
        out.add(m.get("status.walls", walls,
                settings.isServerWalls() ? m.get("on") : m.get("off"),
                AudioDistancePlugin.SERVER_WALLS.activeStreams(), settings.getMaxStreams()));
        out.add(m.get("status.players", ctx.addonPlayers(), ctx.onlinePlayers()));
        out.add(m.get("status.profile", settings.getProfileMode().getId(), settings.getProfilePreset(),
                p.getModel().getId(), p.isReverbEnabled() ? pct(p.getReverbStrength()) : m.get("off")));
        out.add(m.get("status.locks", DistanceConfig.Part.format(settings.getLockedParts()),
                settings.isMonitorAllowed() ? m.get("on") : m.get("off")));
        out.add(m.get("status.rules", pct(settings.getSneakMultiplier()),
                settings.isDeadSilent() ? m.get("on") : m.get("off"),
                settings.isSpectatorsOnly() ? m.get("on") : m.get("off"),
                settings.getMegaphoneItem().isEmpty() ? m.get("off")
                        : settings.getMegaphoneItem() + " ×" + fmt(settings.getMegaphoneMultiplier())));
        out.add(m.get("status.require", settings.getRequireAddon().getId(),
                settings.getMinAddonVersion().isEmpty() ? "-" : settings.getMinAddonVersion()));
        out.add(m.get("status.zones", settings.zones().size()));
        out.add(m.get("status.perf", String.format(Locale.ROOT, "%.2f", AudioDistancePlugin.SERVER_WALLS.perf().averageMs())));
    }

    private static void preset(String[] args, ServerSettings settings, Context ctx, Messages m, List<String> out) {
        String name = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (name.equals("export")) {
            out.add(m.get("preset_export"));
            double range = AudioDistancePlugin.serverVoiceDistance();
            out.add(ProfileCode.encode(settings.profileIn(null, range > 0.0 ? range : AudioDistancePlugin.FALLBACK_DISTANCE)));
            return;
        }
        if (name.equals("import")) {
            // The code may have been split by the chat box; spaces are ignored
            String code = args.length > 2 ? String.join("", Arrays.copyOfRange(args, 2, args.length)) : "";
            if (!settings.importProfile(code)) {
                out.add(m.get("usage", "/vcd preset import VP1:..."));
                return;
            }
            saved(settings, ctx, out, m.get("preset_imported"));
            return;
        }
        if (!name.equals(ServerSettings.CUSTOM_PRESET) && ServerSettings.presetByName(name) == null) {
            out.add(m.get("usage", "/vcd preset vanilla|realistic|clear|stealth|custom|export|import <code>"));
            return;
        }
        settings.setProfilePreset(name);
        saved(settings, ctx, out, m.get("preset_set", settings.getProfilePreset()));
    }

    private static void zones(ServerSettings settings, Messages m, List<String> out) {
        Map<String, Zone> zones = settings.zones();
        if (zones.isEmpty()) {
            out.add(m.get("zones.none"));
            return;
        }
        for (Zone z : zones.values()) {
            out.add(describe(z, m));
        }
    }

    private static String describe(Zone z, Messages m) {
        StringBuilder b = new StringBuilder(m.get("zones." + z.kind())).append(' ').append(z.name()).append(':');
        if (z.box() != null) {
            b.append(' ').append(z.box().world()).append(" [").append(z.box().from()).append(" – ").append(z.box().to()).append(']');
        }
        List<String> parts = new ArrayList<>();
        if (z.mode() != null) {
            parts.add("mode " + z.mode().getId());
        }
        if (z.preset() != null) {
            parts.add("preset " + z.preset());
        }
        Zone.Rules r = z.rules();
        if (r.voiceRange() != null) {
            parts.add("voice_range " + fmt(r.voiceRange()));
        }
        if (r.whisperRange() != null) {
            parts.add("whisper_range " + fmt(r.whisperRange()));
        }
        if (r.rangeMultiplier() != null) {
            parts.add("range ×" + fmt(r.rangeMultiplier()));
        }
        if (r.wallsStrength() != null) {
            parts.add("walls " + pct(r.wallsStrength()));
        }
        if (r.echo() != null) {
            parts.add("echo " + (r.echo() <= 0.0 ? "off" : pct(r.echo())));
        }
        if (r.isolated()) {
            parts.add("isolated");
        }
        if (z.priority() != 0) {
            parts.add("priority " + z.priority());
        }
        if (r.enterMessage() != null) {
            parts.add("message \"" + r.enterMessage() + "\"");
        }
        b.append(' ').append(parts.isEmpty() ? "-" : String.join(", ", parts));
        return b.toString();
    }

    private static void zone(String[] args, ServerSettings settings, Context ctx, ServerPlayers.Info me, Messages m, List<String> out) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "pos1", "pos2" -> {
                if (me == null) {
                    out.add(m.get("zone.need_player"));
                    return;
                }
                int[] pos = {(int) Math.floor(me.x()), (int) Math.floor(me.y()), (int) Math.floor(me.z())};
                (action.equals("pos1") ? POS1 : POS2).put(me.id(), pos);
                POS_WORLD.put(me.id(), me.world());
                out.add(m.get("zone.pos", action, pos[0] + "," + pos[1] + "," + pos[2]));
            }
            case "create" -> {
                String name = args.length > 2 ? clean(args[2]) : "";
                if (name.isEmpty()) {
                    out.add(m.get("usage", "/vcd zone create <name> [radius]"));
                    return;
                }
                Zone.Box box;
                if (args.length > 3) {
                    Integer radius = parseInt(args[3]);
                    if (me == null || radius == null || radius < 1 || radius > MAX_RADIUS) {
                        out.add(me == null ? m.get("zone.need_player") : m.get("usage", "/vcd zone create <name> [1-" + MAX_RADIUS + "]"));
                        return;
                    }
                    int x = (int) Math.floor(me.x());
                    int y = (int) Math.floor(me.y());
                    int z = (int) Math.floor(me.z());
                    box = new Zone.Box(me.world(), x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
                } else {
                    UUID id = me == null ? null : me.id();
                    int[] a = id == null ? null : POS1.get(id);
                    int[] b = id == null ? null : POS2.get(id);
                    if (a == null || b == null) {
                        out.add(m.get("zone.need_corners"));
                        return;
                    }
                    box = new Zone.Box(POS_WORLD.getOrDefault(id, me.world()), a[0], a[1], a[2], b[0], b[1], b[2]);
                }
                Zone old = settings.zones().get(Zone.BOX + ":" + name);
                Zone zone = old == null
                        ? new Zone(Zone.BOX, name, null, null, Zone.Rules.NONE, box, 0)
                        : new Zone(Zone.BOX, name, old.mode(), old.preset(), old.rules(), box, old.priority());
                settings.putZone(zone);
                saved(settings, ctx, out, m.get("zone.created", name, box.world(), box.from(), box.to()));
            }
            case "set" -> zoneSet(args, settings, ctx, m, out);
            case "delete", "remove" -> {
                Zone z = args.length > 2 ? settings.findZone(args[2]) : null;
                if (z == null) {
                    out.add(m.get("zone.unknown", args.length > 2 ? args[2] : ""));
                    return;
                }
                settings.removeZone(z.key());
                saved(settings, ctx, out, m.get("zone.deleted", z.name()));
            }
            case "info", "here" -> {
                if (me == null) {
                    out.add(m.get("zone.need_player"));
                    return;
                }
                Zone z = settings.zoneOf(me);
                out.add(z == null ? m.get("zone.nowhere", me.world()) : m.get("zone.here", describe(z, m)));
            }
            case "list", "" -> zones(settings, m, out);
            default -> out.add(m.get("usage", "/vcd zone pos1|pos2|create|set|delete|info|list"));
        }
    }

    private static void zoneSet(String[] args, ServerSettings settings, Context ctx, Messages m, List<String> out) {
        if (args.length < 5) {
            out.add(m.get("usage", "/vcd zone set <name> <" + String.join("|", ZONE_SETTINGS) + "> <value|default>"));
            return;
        }
        Zone z = settings.findZone(args[2]);
        String name = clean(args[2]);
        if (z == null) {
            // A world gets a zone just by setting something on it
            z = new Zone(Zone.WORLD, name, null, null);
        }
        String key = args[3].toLowerCase(Locale.ROOT);
        String value = String.join(" ", Arrays.copyOfRange(args, 4, args.length)).trim();
        boolean reset = value.equalsIgnoreCase("default") || value.equals("-");
        Zone.Rules r = z.rules();
        ServerSettings.ProfileMode mode = z.mode();
        String preset = z.preset();
        int priority = z.priority();
        Double num = reset ? null : parseNumber(value);
        boolean bad = false;
        switch (key) {
            case "mode" -> {
                mode = reset ? null : ServerSettings.ProfileMode.fromId(value, null);
                bad = !reset && mode == null;
            }
            case "preset" -> {
                Preset p = reset ? null : ServerSettings.presetByName(value);
                preset = p == null ? null : ServerSettings.nameOf(p);
                bad = !reset && p == null;
            }
            case "voice_range" -> {
                bad = !reset && (num == null || num < 1 || num > 1000);
                r = new Zone.Rules(bad ? r.voiceRange() : num, r.whisperRange(), r.rangeMultiplier(), r.wallsStrength(), r.echo(), r.isolated(), r.enterMessage());
            }
            case "whisper_range" -> {
                bad = !reset && (num == null || num < 1 || num > 1000);
                r = new Zone.Rules(r.voiceRange(), bad ? r.whisperRange() : num, r.rangeMultiplier(), r.wallsStrength(), r.echo(), r.isolated(), r.enterMessage());
            }
            case "range_multiplier", "range" -> {
                bad = !reset && (num == null || num < 0.05 || num > 10);
                r = new Zone.Rules(r.voiceRange(), r.whisperRange(), bad ? r.rangeMultiplier() : num, r.wallsStrength(), r.echo(), r.isolated(), r.enterMessage());
            }
            case "walls", "walls_strength" -> {
                Double w = reset ? null : parsePercent(value);
                bad = !reset && w == null;
                r = new Zone.Rules(r.voiceRange(), r.whisperRange(), r.rangeMultiplier(), bad ? r.wallsStrength() : w, r.echo(), r.isolated(), r.enterMessage());
            }
            case "echo" -> {
                Double e = reset ? null : ServerSettings.parseEcho(value.endsWith("%") ? String.valueOf(parsePercent(value)) : value);
                bad = !reset && e == null && !value.equalsIgnoreCase("auto");
                r = new Zone.Rules(r.voiceRange(), r.whisperRange(), r.rangeMultiplier(), r.wallsStrength(), bad ? r.echo() : e, r.isolated(), r.enterMessage());
            }
            case "isolated" -> {
                Boolean on = reset ? Boolean.FALSE : parseOnOff(value);
                bad = on == null;
                r = new Zone.Rules(r.voiceRange(), r.whisperRange(), r.rangeMultiplier(), r.wallsStrength(), r.echo(), bad ? r.isolated() : on, r.enterMessage());
            }
            case "message", "enter_message" -> r = new Zone.Rules(r.voiceRange(), r.whisperRange(), r.rangeMultiplier(), r.wallsStrength(), r.echo(), r.isolated(), reset || value.isEmpty() ? null : value);
            case "priority" -> {
                Integer pr = reset ? Integer.valueOf(0) : parseInt(value);
                bad = pr == null;
                priority = bad ? priority : pr;
            }
            default -> {
                out.add(m.get("usage", "/vcd zone set <name> <" + String.join("|", ZONE_SETTINGS) + "> <value|default>"));
                return;
            }
        }
        if (bad) {
            out.add(m.get("zone.bad_value", key, value));
            return;
        }
        Zone updated = new Zone(z.kind(), z.name(), mode, preset, r, z.box(), priority);
        if (updated.box() == null && updated.mode() == null && updated.preset() == null && updated.rules().isEmpty()) {
            // A world or region zone with nothing left is removed
            settings.removeZone(updated.key());
        } else {
            settings.putZone(updated);
        }
        saved(settings, ctx, out, m.get("zone.set", z.name(), key, reset ? m.get("default") : value));
    }

    private static void rule(String[] args, ServerSettings settings, Context ctx, Messages m, List<String> out) {
        String name = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        String value = args.length > 2 ? args[2] : "";
        switch (name) {
            case "sneak" -> {
                Double v = parseNumber(value.endsWith("%") ? String.valueOf(parsePercent(value)) : value);
                if (v == null || v < 0.1 || v > 1.0) {
                    out.add(m.get("usage", "/vcd rule sneak 0.1-1"));
                    return;
                }
                settings.setSneakMultiplier(v);
                saved(settings, ctx, out, m.get("rule.sneak", pct(settings.getSneakMultiplier())));
            }
            case "dead", "spectators" -> {
                Boolean on = parseOnOff(value);
                if (on == null) {
                    out.add(m.get("usage", "/vcd rule " + name + " on|off"));
                    return;
                }
                if (name.equals("dead")) {
                    settings.setDeadSilent(on);
                } else {
                    settings.setSpectatorsOnly(on);
                }
                saved(settings, ctx, out, m.get("rule." + name + (on ? ".on" : ".off")));
            }
            case "megaphone" -> {
                if (value.isEmpty()) {
                    out.add(m.get("usage", "/vcd rule megaphone <item id>|off"));
                    return;
                }
                settings.setMegaphoneItem(value);
                saved(settings, ctx, out, settings.getMegaphoneItem().isEmpty() ? m.get("rule.megaphone.off")
                        : m.get("rule.megaphone.on", settings.getMegaphoneItem(), fmt(settings.getMegaphoneMultiplier())));
            }
            case "megaphone_range" -> {
                Double v = parseNumber(value);
                if (v == null || v < 1.0 || v > 10.0) {
                    out.add(m.get("usage", "/vcd rule megaphone_range 1-10"));
                    return;
                }
                settings.setMegaphoneMultiplier(v);
                saved(settings, ctx, out, m.get("rule.megaphone_range", fmt(settings.getMegaphoneMultiplier())));
            }
            default -> out.add(m.get("usage", "/vcd rule " + String.join("|", RULES) + " <value>"));
        }
    }

    private static void require(String[] args, ServerSettings settings, Context ctx, Messages m, List<String> out) {
        ServerSettings.RequireAddon mode = args.length > 1 ? ServerSettings.RequireAddon.fromId(args[1], null) : null;
        if (mode == null) {
            out.add(m.get("usage", "/vcd require off|suggest|warn|kick [min version]"));
            return;
        }
        settings.setRequireAddon(mode);
        if (args.length > 2) {
            settings.setMinAddonVersion(args[2].equals("-") || args[2].equalsIgnoreCase("any") ? "" : args[2]);
        }
        saved(settings, ctx, out, m.get("require.set", mode.getId(),
                settings.getMinAddonVersion().isEmpty() ? "-" : settings.getMinAddonVersion()));
    }

    private static void debug(String[] args, ServerSettings settings, Context ctx, Messages m, List<String> out) {
        ServerPlayers players = ctx.players();
        ServerPlayers.Info target = args.length > 1 ? players.byName(args[1]) : null;
        if (target == null) {
            out.add(args.length > 1 ? m.get("debug.unknown", args[1]) : m.get("usage", "/vcd debug <player>"));
            return;
        }
        double voice = AudioDistancePlugin.serverVoiceDistance();
        double whisper = AudioDistancePlugin.serverWhisperDistance();
        if (voice <= 0.0) {
            voice = AudioDistancePlugin.FALLBACK_DISTANCE;
            whisper = voice / 2.0;
        }
        Zone zone = settings.zoneOf(target);
        String version = AudioDistancePlugin.ADDON_CHECK.version(target.id());
        out.add(m.get("debug.title", target.name()));
        out.add(m.get("debug.where", target.world(), fmt(target.x()) + " " + fmt(target.y()) + " " + fmt(target.z()),
                zone == null ? "-" : zone.name()));
        out.add(m.get("debug.addon", version == null ? m.get("debug.no_addon") : version,
                ServerRange.isMegaphone(settings, target) ? m.get("on") : m.get("off"),
                target.sneaking() ? m.get("on") : m.get("off")));
        out.add(m.get("debug.range", fmt(ServerRange.rangeOf(settings, target, false, voice, whisper)),
                fmt(ServerRange.rangeOf(settings, target, true, voice, whisper))));
        int shown = 0;
        List<ServerPlayers.Info> others = new ArrayList<>(players.all());
        others.sort((a, b) -> Double.compare(a.distanceTo(target), b.distanceTo(target)));
        for (ServerPlayers.Info other : others) {
            if (other.id().equals(target.id()) || !Zone.sameWorld(other.world(), target.world())) {
                continue;
            }
            if (shown++ == 8) {
                break;
            }
            // How the target hears them, and how they hear the target
            ServerRange.Decision in = ServerRange.decide(settings, other, target, false, voice, whisper);
            ServerRange.Decision outgoing = ServerRange.decide(settings, target, other, false, voice, whisper);
            out.add(m.get("debug.line", other.name(), fmt(other.distanceTo(target)),
                    m.get("debug.reason." + in.reason().name().toLowerCase(Locale.ROOT)),
                    m.get("debug.reason." + outgoing.reason().name().toLowerCase(Locale.ROOT))));
        }
        if (shown == 0) {
            out.add(m.get("debug.alone"));
        }
    }

    private static void help(Messages m, List<String> out) {
        out.add(m.get("help.title"));
        for (String line : new String[]{"status", "reload", "profile", "preset", "walls", "serverwalls", "lock", "monitor", "zones", "zone",
                "rule", "require", "debug"}) {
            out.add(m.get("help." + line));
        }
    }

    private static void saved(ServerSettings settings, Context ctx, List<String> out, String line) {
        settings.save();
        ctx.afterSettingsChange();
        ctx.resendProfiles();
        out.add(line);
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    static Double parsePercent(String s) {
        String v = s.trim().toLowerCase(Locale.ROOT);
        if (v.equals("off")) {
            return 0.0;
        }
        if (v.endsWith("%")) {
            v = v.substring(0, v.length() - 1);
        }
        try {
            double d = Double.parseDouble(v);
            if (!Double.isFinite(d) || d < 0.0) {
                return null;
            }
            // 0.6 and 60 both mean 60%
            return Math.min(1.0, d > 1.0 ? d / 100.0 : d);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Boolean parseOnOff(String s) {
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> Boolean.TRUE;
            case "off", "false", "no", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static Double parseNumber(String s) {
        try {
            double d = Double.parseDouble(s.trim().replace(',', '.'));
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Zone names: letters, digits, '_' and '-' (they become settings keys). */
    private static String clean(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
    }

    private static void addMatching(List<String> out, String[] options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.startsWith(p)) {
                out.add(o);
            }
        }
    }

    private static String pct(double v) {
        return Math.round(v * 100.0) + "%";
    }

    private static String fmt(double v) {
        return Math.abs(v - Math.rint(v)) < 0.05 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    /** Reply texts in the chosen language (the {@code command.vc-audio-distance.*} keys). */
    static final class Messages {

        private final String language;

        Messages(String language) {
            this.language = language;
        }

        String get(String key, Object... args) {
            return ServerText.get(language, key, args);
        }
    }
}
