package com.kasper.vcdistance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Server-side settings, stored in {@code config/vc-audio-distance-server.properties}.
 * <p>
 * The file holds the server's sound profile (the same keys as the client config, prefixed with
 * {@code profile.}), how that profile is offered to players who have the addon, and the switch for
 * server-side wall muffling for players who do not have it. The file is re-read automatically when
 * it changes on disk.
 */
public final class ServerSettings {

    private static final String FILE_NAME = "vc-audio-distance-server.properties";
    private static final String PROFILE_PREFIX = "profile.";

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
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            DistanceConfig.LOGGER.error("Failed to read {}, keeping previous server settings: {}", file, e.getMessage());
            return;
        }
        profileMode = ProfileMode.fromId(props.getProperty("profile_mode"), ProfileMode.OFF);
        serverWalls = DistanceConfig.parseBoolean(props, "server_walls", true);
        maxStreams = (int) DistanceConfig.clamp(DistanceConfig.parseDouble(props, "server_walls_max_streams", DEFAULT_MAX_STREAMS), 0, MAX_STREAMS_LIMIT);
        profile.readFrom(props, PROFILE_PREFIX);
        loadedModified = lastModified(file);
        DistanceConfig.LOGGER.info("Server settings loaded: profile {}, server walls {} (max {} voices)",
                profileMode.getId(), serverWalls, maxStreams);
    }

    public synchronized void save() {
        Properties props = new Properties();
        props.setProperty("profile_mode", profileMode.getId());
        props.setProperty("server_walls", String.valueOf(serverWalls));
        props.setProperty("server_walls_max_streams", String.valueOf(maxStreams));
        profile.writeTo(props, PROFILE_PREFIX);
        DistanceConfig.store(getPath(), props, "VoiceChat Audio Distance - server settings\n"
                + "Changes are picked up automatically, no restart needed.\n"
                + "\n"
                + "profile_mode: how players WITH the addon get the profile below\n"
                + "  off     - they keep their own settings\n"
                + "  suggest - they see it in their settings screen and can apply it\n"
                + "  enforce - they hear it while on this server (fair play for PvP / events)\n"
                + "profile.*: the server's sound profile, same keys as the client config\n"
                + "\n"
                + "server_walls: muffle voices through walls for players WITHOUT the addon\n"
                + "  (uses profile.occlusion_* and profile.material.*)\n"
                + "server_walls_max_streams: most voices re-encoded at once (CPU limit, 0 - 512)");
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
        return String.format(Locale.ROOT, "ServerSettings[mode=%s, walls=%s, max=%d]", profileMode.getId(), serverWalls, maxStreams);
    }
}
