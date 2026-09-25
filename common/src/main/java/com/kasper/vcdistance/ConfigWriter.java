package com.kasper.vcdistance;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;

/**
 * Writes a settings file for people: keys in a fixed order, grouped under headings, each with a
 * comment in English and then Russian. {@link Properties} would write the keys in hash order with
 * a single header, which is hard to read.
 * <p>
 * Files are UTF-8. Values are plain ASCII (numbers, ids, true/false), so the files still load as
 * ordinary properties.
 */
final class ConfigWriter {

    private static final String RULE = "# " + "=".repeat(78);

    private final StringBuilder text = new StringBuilder();

    /** A framed title block: English lines, then Russian lines. */
    ConfigWriter title(String... lines) {
        text.append(RULE).append('\n');
        for (String line : lines) {
            text.append(line.isEmpty() ? "#" : "# " + line).append('\n');
        }
        text.append(RULE).append('\n');
        return this;
    }

    /** A section heading. */
    ConfigWriter section(String english, String russian) {
        text.append('\n').append("# ---------- ").append(english).append(" / ").append(russian)
                .append(" ").append("-".repeat(Math.max(4, 60 - english.length() - russian.length())))
                .append('\n');
        return this;
    }

    /** Comment lines above the next key; pass English lines first, then Russian. */
    ConfigWriter comment(String... lines) {
        text.append('\n');
        for (String line : lines) {
            text.append(line.isEmpty() ? "#" : "# " + line).append('\n');
        }
        return this;
    }

    ConfigWriter value(String key, String value) {
        text.append(key).append('=').append(value).append('\n');
        return this;
    }

    ConfigWriter value(String key, double value) {
        return value(key, number(value));
    }

    ConfigWriter value(String key, int value) {
        return value(key, String.valueOf(value));
    }

    ConfigWriter value(String key, boolean value) {
        return value(key, String.valueOf(value));
    }

    @Override
    public String toString() {
        return text.toString();
    }

    /** Writes the file atomically (temp file + move). */
    void save(Path file) {
        try {
            Path dir = file.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp);
                 Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                w.write(text.toString());
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            DistanceConfig.LOGGER.error("Failed to save {}: {}", file, e.getMessage());
        }
    }

    /** Reads a settings file as UTF-8; broken characters in comments are replaced, not fatal. */
    static Properties load(Path file) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        return props;
    }

    /** Short number form for people: 1.0, 0.6, 0.05 instead of 1.0000. */
    static String number(double value) {
        String s = String.format(Locale.ROOT, "%.4f", value);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s + "0" : s;
    }
}
