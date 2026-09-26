package com.kasper.vcdistance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code /vcd} command for server admins, the same on Fabric and Paper: the platform only
 * passes the typed text and prints the reply lines. Changes are saved to the settings file and sent
 * to the players who have the addon right away.
 * <pre>
 * /vcd status                                  what the addon is doing now
 * /vcd reload                                  re-read the settings file
 * /vcd profile off|suggest|enforce             how the profile is offered
 * /vcd preset vanilla|realistic|clear|stealth|custom
 * /vcd preset export | import &lt;code&gt;             the profile as a code players can paste
 * /vcd walls 0-100|off                         wall strength for everyone, in %
 * /vcd serverwalls on|off                      walls for players without the addon
 * /vcd zones                                   worlds and regions with their own profile
 * </pre>
 */
public final class AdminCommands {

    public static final String NAME = "vcd";
    static final String[] SUBCOMMANDS = {"status", "reload", "profile", "preset", "walls", "serverwalls", "zones", "help"};
    static final String[] MODES = {"off", "suggest", "enforce"};
    static final String[] PRESETS = {"vanilla", "realistic", "clear", "stealth", "custom", "export", "import"};

    /** What the platform provides to the command. */
    public interface Context {

        /** "Fabric" or "Paper", for the status line. */
        String platform();

        int onlinePlayers();

        int addonPlayers();

        /** Sends every player with the addon their (zone's) profile again. */
        void resendProfiles();

        /** Drops caches that depend on the settings (block acoustics). */
        default void afterSettingsChange() {
        }
    }

    private AdminCommands() {
    }

    /**
     * Runs a command.
     *
     * @param input everything typed after {@code /vcd}, possibly empty
     * @return the reply, one line per entry
     */
    public static List<String> run(String input, ServerSettings settings, Context ctx) {
        Messages m = new Messages(settings.getMessagesLanguage());
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
            case "preset" -> {
                String name = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                if (name.equals("export")) {
                    out.add(m.get("preset_export"));
                    out.add(ProfileCode.encode(settings.profileIn(null, AudioDistancePlugin.serverVoiceDistance() > 0.0
                            ? AudioDistancePlugin.serverVoiceDistance() : AudioDistancePlugin.FALLBACK_DISTANCE)));
                    break;
                }
                if (name.equals("import")) {
                    // The code may have been split by the chat box; spaces are ignored
                    String code = args.length > 2 ? String.join("", java.util.Arrays.copyOfRange(args, 2, args.length)) : "";
                    if (!settings.importProfile(code)) {
                        out.add(m.get("usage", "/vcd preset import VP1:..."));
                        break;
                    }
                    saved(settings, ctx, out, m.get("preset_imported"));
                    break;
                }
                if (!name.equals(ServerSettings.CUSTOM_PRESET) && ServerSettings.presetByName(name) == null) {
                    out.add(m.get("usage", "/vcd preset vanilla|realistic|clear|stealth|custom|export|import <code>"));
                    break;
                }
                settings.setProfilePreset(name);
                saved(settings, ctx, out, m.get("preset_set", settings.getProfilePreset()));
            }
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
            case "zones" -> zones(settings, m, out);
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
                case "serverwalls" -> new String[]{"on", "off"};
                default -> new String[0];
            };
            addMatching(out, options, args[1]);
        }
        return out;
    }

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
        out.add(m.get("status.zones", settings.zones().size()));
        out.add(m.get("status.perf", String.format(Locale.ROOT, "%.2f", AudioDistancePlugin.SERVER_WALLS.perf().averageMs())));
    }

    private static void zones(ServerSettings settings, Messages m, List<String> out) {
        Map<String, Zone> zones = settings.zones();
        if (zones.isEmpty()) {
            out.add(m.get("zones.none"));
            return;
        }
        for (Zone z : zones.values()) {
            out.add(m.get("zones.line", m.get("zones." + z.kind()), z.name(),
                    z.mode() == null ? "-" : z.mode().getId(), z.preset() == null ? "-" : z.preset()));
        }
    }

    private static void help(Messages m, List<String> out) {
        out.add(m.get("help.title"));
        for (String line : new String[]{"status", "reload", "profile", "preset", "walls", "serverwalls", "zones"}) {
            out.add(m.get("help." + line));
        }
    }

    private static void saved(ServerSettings settings, Context ctx, List<String> out, String line) {
        settings.save();
        ctx.afterSettingsChange();
        ctx.resendProfiles();
        out.add(line);
    }

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

    /** Reply texts in English and Russian. */
    static final class Messages {

        private static final Map<String, String[]> TEXT = Map.ofEntries(
                Map.entry("on", new String[]{"on", "вкл."}),
                Map.entry("off", new String[]{"off", "выкл."}),
                Map.entry("usage", new String[]{"Usage: %s", "Использование: %s"}),
                Map.entry("reloaded", new String[]{"Settings reloaded from %s and sent to players with the addon.",
                        "Настройки перечитаны из %s и отправлены игрокам с аддоном."}),
                Map.entry("profile_set", new String[]{"Profile mode: %s. Saved and sent to players with the addon.",
                        "Режим профиля: %s. Сохранено и отправлено игрокам с аддоном."}),
                Map.entry("preset_set", new String[]{"Profile preset: %s. Saved and sent to players with the addon.",
                        "Пресет профиля: %s. Сохранено и отправлено игрокам с аддоном."}),
                Map.entry("preset_export", new String[]{"The server's profile as a code (players paste it on the Distance tab, servers with /vcd preset import):",
                        "Профиль сервера в виде кода (игроки вставляют его на вкладке «Дистанция», серверы - через /vcd preset import):"}),
                Map.entry("preset_imported", new String[]{"Profile imported from the code (preset: custom). Saved and sent to players with the addon.",
                        "Профиль загружен из кода (пресет: custom). Сохранено и отправлено игрокам с аддоном."}),
                Map.entry("status.perf", new String[]{"Load of the walls for players without the addon: %s ms per tick",
                        "Нагрузка стен для игроков без аддона: %s мс за тик"}),
                Map.entry("walls_set", new String[]{"Walls: %s for everyone. Saved.", "Стены: %s для всех. Сохранено."}),
                Map.entry("walls_off", new String[]{"Walls are off for everyone. Saved.", "Стены выключены для всех. Сохранено."}),
                Map.entry("serverwalls_on", new String[]{"The server muffles walls for players without the addon. Saved.",
                        "Сервер глушит стены для игроков без аддона. Сохранено."}),
                Map.entry("serverwalls_off", new String[]{"Players without the addon hear voices without walls. Saved.",
                        "Игроки без аддона слышат голоса без стен. Сохранено."}),
                Map.entry("status.title", new String[]{"Voice Physics %s on %s", "Voice Physics %s на %s"}),
                Map.entry("status.svc", new String[]{"Simple Voice Chat: running, voice %s bl., whisper %s bl.",
                        "Simple Voice Chat: работает, голос %s бл., шёпот %s бл."}),
                Map.entry("status.svc_off", new String[]{"Simple Voice Chat: not running yet", "Simple Voice Chat: ещё не запущен"}),
                Map.entry("status.walls", new String[]{"Walls: %s · for players without the addon: %s (%s of %s voices now)",
                        "Стены: %s · для игроков без аддона: %s (сейчас %s из %s голосов)"}),
                Map.entry("status.players", new String[]{"Players with the addon: %s of %s", "Игроков с аддоном: %s из %s"}),
                Map.entry("status.profile", new String[]{"Profile: %s, preset %s, curve %s, echo %s",
                        "Профиль: %s, пресет %s, кривая %s, эхо %s"}),
                Map.entry("status.zones", new String[]{"Zones: %s (/vcd zones)", "Зон: %s (/vcd zones)"}),
                Map.entry("zones.none", new String[]{"No zones. Add zone.world.<world>.profile_preset=... to the settings file.",
                        "Зон нет. Добавьте zone.world.<мир>.profile_preset=... в файл настроек."}),
                Map.entry("zones.line", new String[]{"%s %s: mode %s, preset %s", "%s %s: режим %s, пресет %s"}),
                Map.entry("zones.world", new String[]{"World", "Мир"}),
                Map.entry("zones.region", new String[]{"Region", "Регион"}),
                Map.entry("help.title", new String[]{"Voice Physics commands:", "Команды Voice Physics:"}),
                Map.entry("help.status", new String[]{"/vcd status - what the addon is doing now", "/vcd status - что аддон делает сейчас"}),
                Map.entry("help.reload", new String[]{"/vcd reload - re-read the settings file", "/vcd reload - перечитать файл настроек"}),
                Map.entry("help.profile", new String[]{"/vcd profile off|suggest|enforce - how the profile is offered",
                        "/vcd profile off|suggest|enforce - как предлагать профиль"}),
                Map.entry("help.preset", new String[]{"/vcd preset vanilla|realistic|clear|stealth|custom - the server's sound; export | import <code> - as a code",
                        "/vcd preset vanilla|realistic|clear|stealth|custom - звук сервера; export | import <код> - в виде кода"}),
                Map.entry("help.walls", new String[]{"/vcd walls 0-100|off - wall strength for everyone, in %",
                        "/vcd walls 0-100|off - сила стен для всех, в %"}),
                Map.entry("help.serverwalls", new String[]{"/vcd serverwalls on|off - walls for players without the addon",
                        "/vcd serverwalls on|off - стены для игроков без аддона"}),
                Map.entry("help.zones", new String[]{"/vcd zones - worlds and regions with their own profile",
                        "/vcd zones - миры и регионы со своим профилем"})
        );

        private final int index;

        Messages(String language) {
            this.index = "ru".equals(language) ? 1 : 0;
        }

        String get(String key, Object... args) {
            String[] t = TEXT.get(key);
            String text = t == null ? key : t[index];
            return args.length == 0 ? text : String.format(Locale.ROOT, text, args);
        }
    }
}
