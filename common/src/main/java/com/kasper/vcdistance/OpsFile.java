package com.kasper.vcdistance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Operator levels from the server's {@code ops.json}. Used only where Minecraft's own permission
 * check changed between the versions one jar supports, so the file (whose format never changed) is
 * the one stable source. Re-read when it changes on disk.
 */
public final class OpsFile {

    private static final Pattern ENTRY = Pattern.compile("\\{[^{}]*}");
    private static final Pattern UUID_FIELD = Pattern.compile("\"uuid\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"");
    private static final Pattern LEVEL_FIELD = Pattern.compile("\"level\"\\s*:\\s*(\\d+)");

    private final Path file;
    private long modified = Long.MIN_VALUE;
    private Map<UUID, Integer> levels = Map.of();

    public OpsFile(Path file) {
        this.file = file;
    }

    /** The player's operator level, 0 when they are not an operator. */
    public synchronized int level(UUID player) {
        refresh();
        Integer l = levels.get(player);
        return l == null ? 0 : l;
    }

    private void refresh() {
        try {
            long m = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : Long.MIN_VALUE + 1;
            if (m == modified) {
                return;
            }
            modified = m;
            levels = Files.exists(file) ? parse(Files.readString(file, StandardCharsets.UTF_8)) : Map.of();
        } catch (IOException | RuntimeException e) {
            levels = Map.of();
        }
    }

    static Map<UUID, Integer> parse(String json) {
        Map<UUID, Integer> out = new HashMap<>();
        Matcher entry = ENTRY.matcher(json);
        while (entry.find()) {
            String e = entry.group();
            Matcher u = UUID_FIELD.matcher(e);
            Matcher l = LEVEL_FIELD.matcher(e);
            if (u.find()) {
                try {
                    out.put(UUID.fromString(u.group(1)), l.find() ? Integer.parseInt(l.group(1)) : 4);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return out;
    }
}
