package com.kasper.vcdistance;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards against untranslated keys showing up in the UI: every key the code uses must exist in
 * every language file, and all language files must have the same keys and placeholders.
 */
public class LangConsistencyTest {

    private static final String PREFIX = "gui.vc-audio-distance.";
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path LANG = ROOT.resolve("common/src/main/resources/assets/vc-audio-distance/lang");

    private static Map<String, String> english;
    private static Map<String, String> russian;
    private static String sources;

    @BeforeAll
    static void load() throws IOException {
        english = readLang(LANG.resolve("en_us.json"));
        russian = readLang(LANG.resolve("ru_ru.json"));
        StringBuilder all = new StringBuilder();
        for (String dir : new String[]{"common/src/main", "shared", "fabric-1.20/src", "fabric-1.21/src", "fabric-26/src"}) {
            Path p = ROOT.resolve(dir);
            if (!Files.isDirectory(p)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(p)) {
                for (Path f : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    all.append(Files.readString(f, StandardCharsets.UTF_8)).append('\n');
                }
            }
        }
        sources = all.toString();
    }

    @Test
    @DisplayName("All language files contain the same keys")
    void sameKeys() {
        assertEquals(new TreeSet<>(english.keySet()), new TreeSet<>(russian.keySet()));
    }

    @Test
    @DisplayName("Placeholders match between languages")
    void samePlaceholders() {
        for (String key : english.keySet()) {
            assertEquals(placeholders(english.get(key)), placeholders(russian.get(key)), "Placeholder count differs for " + key);
        }
    }

    @Test
    @DisplayName("Every key used by the code is translated")
    void usedKeysExist() {
        Set<String> used = new TreeSet<>();

        Matcher full = Pattern.compile("\"((?:gui|key|message)\\.vc-audio-distance[\\w.\\-]*)\"").matcher(sources);
        while (full.find()) {
            String k = full.group(1);
            if (!k.endsWith(".")) {
                used.add(k);
            }
        }
        Matcher relative = Pattern.compile("\\btr\\(\"([\\w.]+)\"").matcher(sources);
        while (relative.find()) {
            String k = relative.group(1);
            if (!k.endsWith(".")) {
                used.add(PREFIX + k);
            }
        }
        Matcher tabs = Pattern.compile("\\(\"(tab\\.\\w+)\"\\)").matcher(sources);
        while (tabs.find()) {
            used.add(PREFIX + tabs.group(1));
        }
        Matcher examples = Pattern.compile("new Example\\(\"(\\w+)\"").matcher(sources);
        while (examples.find()) {
            used.add(PREFIX + "walls.example." + examples.group(1));
        }
        for (AudioDistancePlugin.OcclusionStatus s : AudioDistancePlugin.OcclusionStatus.values()) {
            used.add(PREFIX + "monitor.status." + s.name().toLowerCase(Locale.ROOT));
        }
        for (ServerSettings.ProfileMode m : ServerSettings.ProfileMode.values()) {
            used.add(PREFIX + "monitor.server.mode." + m.getId());
        }
        for (VoiceState v : VoiceState.values()) {
            used.add(PREFIX + "monitor.state." + v.getTranslationKey());
        }
        used.add(PREFIX + "monitor.state.silent");
        for (HudMode m : HudMode.values()) {
            used.add(m.getTranslationKey());
        }
        for (HudCorner c : HudCorner.values()) {
            used.add(c.getTranslationKey());
        }
        // The voice HUD builds its keys from a base and a "_whisper" variant
        Matcher hud = Pattern.compile("\\bhud\\(\"([\\w.]+)\"").matcher(sources);
        while (hud.find()) {
            used.add(PREFIX + "hud." + hud.group(1));
        }
        for (String k : new String[]{"nobody", "in_range", "hears"}) {
            used.add(PREFIX + "hud." + k + "_whisper");
        }
        used.add("key.vc-audio-distance.toggle_hud");
        used.add("message.vc-audio-distance.server_profile.suggest");
        used.add("message.vc-audio-distance.server_profile.enforce");
        for (String s : new String[]{"status.sound_physics", "status.unavailable"}) {
            used.add(PREFIX + s);
            used.add(PREFIX + s + ".detail");
        }
        for (AttenuationModel m : AttenuationModel.values()) {
            used.add(m.getTranslationKey());
            used.add(m.getTooltipKey());
        }
        for (Preset p : Preset.values()) {
            used.add(p.getTranslationKey());
            used.add(p.getTooltipKey());
        }
        for (AcousticMaterial m : AcousticMaterial.values()) {
            used.add(m.getTranslationKey());
            used.add(m.getTooltipKey());
        }

        List<String> missing = new ArrayList<>();
        for (String k : used) {
            if (!english.containsKey(k)) {
                missing.add(k);
            }
        }
        assertTrue(missing.isEmpty(), "Missing translations: " + missing);
    }

    private static int placeholders(String s) {
        Matcher m = Pattern.compile("%(?:\\d+\\$)?s").matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** Minimal reader for flat {"key": "value"} JSON files. */
    private static Map<String, String> readLang(Path file) throws IOException {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, String> map = new TreeMap<>();
        Matcher m = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        while (m.find()) {
            assertNull(map.put(m.group(1), m.group(2)), "Duplicate key " + m.group(1) + " in " + file.getFileName());
        }
        assertFalse(map.isEmpty(), "No entries in " + file);
        return map;
    }
}
