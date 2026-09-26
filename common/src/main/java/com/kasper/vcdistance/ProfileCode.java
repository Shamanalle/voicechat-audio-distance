package com.kasper.vcdistance;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A sound profile as one line of text, to share with friends or paste into a server's settings:
 * {@code VP1:} followed by the compressed profile. It holds everything a server profile holds (curve,
 * walls, materials, effects), not the interface settings.
 */
public final class ProfileCode {

    public static final String PREFIX = "VP1:";
    /** Longest profile text accepted when unpacking; a real one is about 1 KB. */
    private static final int MAX_TEXT = 16 * 1024;

    private ProfileCode() {
    }

    public static String encode(DistanceConfig config) {
        Properties p = new Properties();
        config.writeTo(p, "");
        List<String> keys = new ArrayList<>(p.stringPropertyNames());
        keys.sort(null);
        StringBuilder text = new StringBuilder();
        for (String k : keys) {
            text.append(k).append('=').append(trim(p.getProperty(k))).append('\n');
        }
        byte[] raw = text.toString().getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[512];
        while (!deflater.finished()) {
            out.write(buf, 0, deflater.deflate(buf));
        }
        deflater.end();
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    /**
     * Reads a code into {@code into}. Values the code does not have fall back to the defaults.
     *
     * @return {@code false} (and {@code into} untouched) when the text is not a valid code
     */
    public static boolean decode(String code, DistanceConfig into) {
        Properties p = parse(code);
        if (p == null) {
            return false;
        }
        into.readFrom(p, "");
        return true;
    }

    /** Whether the text is a valid code. */
    public static boolean isValid(String code) {
        return parse(code) != null;
    }

    private static Properties parse(String code) {
        if (code == null) {
            return null;
        }
        String s = code.replaceAll("\\s+", "");
        if (!s.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return null;
        }
        try {
            byte[] packed = Base64.getUrlDecoder().decode(s.substring(PREFIX.length()));
            Inflater inflater = new Inflater(true);
            inflater.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[512];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                out.write(buf, 0, n);
                if (out.size() > MAX_TEXT) {
                    inflater.end();
                    return null;
                }
            }
            boolean complete = inflater.finished();
            inflater.end();
            if (!complete) {
                return null;
            }
            Properties p = new Properties();
            for (String line : out.toString(StandardCharsets.UTF_8).split("\n")) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    p.setProperty(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }
            return p.getProperty("distance_model") != null ? p : null;
        } catch (IllegalArgumentException | DataFormatException e) {
            return null;
        }
    }

    /** "0.7000" -> "0.7" to keep codes short. */
    private static String trim(String v) {
        if (v.indexOf('.') < 0) {
            return v;
        }
        String t = v.replaceAll("0+$", "");
        return t.endsWith(".") ? t.substring(0, t.length() - 1) : t;
    }
}
