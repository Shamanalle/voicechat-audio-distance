package com.kasper.vcdistance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Texts the server sends (command replies, notices to players), from the same language files as the
 * client's screens: the {@code command.vc-audio-distance.*} keys of
 * {@code assets/vc-audio-distance/lang/<language>.json}. A server has no Minecraft translation
 * system of its own for mod texts, so the files are read here, once per language.
 */
public final class ServerText {

    public static final String PREFIX = "command.vc-audio-distance.";
    public static final String DEFAULT_LANGUAGE = "en_us";
    /** The languages that have a file. */
    public static final String[] LANGUAGES = {"en_us", "ru_ru", "uk_ua", "de_de", "es_es", "pt_br", "zh_cn"};

    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    private ServerText() {
    }

    /**
     * The language file for a setting or a player's client language: "ru", "ru_ru", "ru-RU" and "RU"
     * all give "ru_ru"; anything unknown gives English.
     */
    public static String language(String code) {
        if (code == null || code.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        String c = code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (String l : LANGUAGES) {
            if (l.equals(c) || l.startsWith(c + "_")) {
                return l;
            }
        }
        // Another country's variant of a known language (pt_pt, es_mx, de_at, uk, zh_tw...)
        int u = c.indexOf('_');
        String base = u > 0 ? c.substring(0, u) : c;
        for (String l : LANGUAGES) {
            if (l.startsWith(base + "_")) {
                return l;
            }
        }
        return DEFAULT_LANGUAGE;
    }

    /** {@code key} (without the prefix) in {@code language}, with {@code %s} filled in; English when missing. */
    public static String get(String language, String key, Object... args) {
        String text = table(language(language)).get(PREFIX + key);
        if (text == null) {
            text = table(DEFAULT_LANGUAGE).getOrDefault(PREFIX + key, key);
        }
        try {
            return args.length == 0 ? text : String.format(Locale.ROOT, text, args);
        } catch (RuntimeException e) {
            return text;
        }
    }

    private static Map<String, String> table(String language) {
        return CACHE.computeIfAbsent(language, ServerText::load);
    }

    private static Map<String, String> load(String language) {
        String path = "/assets/vc-audio-distance/lang/" + language + ".json";
        try (InputStream in = ServerText.class.getResourceAsStream(path)) {
            if (in == null) {
                return Map.of();
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            DistanceConfig.LOGGER.warn("Could not read {}: {}", path, e.toString());
            return Map.of();
        }
    }

    /** Reads a flat JSON object of strings ({"key": "value", ...}), which is all a language file is. */
    static Map<String, String> parse(String json) {
        Map<String, String> out = new HashMap<>();
        int[] i = {json.indexOf('{') + 1};
        while (true) {
            skipSpace(json, i);
            if (i[0] >= json.length() || json.charAt(i[0]) == '}') {
                return out;
            }
            String key = string(json, i);
            skipSpace(json, i);
            expect(json, i, ':');
            skipSpace(json, i);
            String value = string(json, i);
            out.put(key, value);
            skipSpace(json, i);
            if (i[0] < json.length() && json.charAt(i[0]) == ',') {
                i[0]++;
            }
        }
    }

    private static void skipSpace(String s, int[] i) {
        while (i[0] < s.length() && Character.isWhitespace(s.charAt(i[0]))) {
            i[0]++;
        }
    }

    private static void expect(String s, int[] i, char c) {
        if (i[0] >= s.length() || s.charAt(i[0]) != c) {
            throw new IllegalArgumentException("Expected '" + c + "' at " + i[0]);
        }
        i[0]++;
    }

    private static String string(String s, int[] i) {
        expect(s, i, '"');
        StringBuilder b = new StringBuilder();
        while (i[0] < s.length()) {
            char c = s.charAt(i[0]++);
            if (c == '"') {
                return b.toString();
            }
            if (c != '\\') {
                b.append(c);
                continue;
            }
            char e = s.charAt(i[0]++);
            switch (e) {
                case 'n' -> b.append('\n');
                case 't' -> b.append('\t');
                case 'r' -> b.append('\r');
                case 'b' -> b.append('\b');
                case 'f' -> b.append('\f');
                case 'u' -> {
                    b.append((char) Integer.parseInt(s.substring(i[0], i[0] + 4), 16));
                    i[0] += 4;
                }
                default -> b.append(e);
            }
        }
        throw new IllegalArgumentException("Unterminated string");
    }
}
