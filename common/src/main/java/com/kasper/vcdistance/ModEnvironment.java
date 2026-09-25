package com.kasper.vcdistance;

import java.nio.file.Path;

/**
 * Loader-agnostic environment lookups done reflectively, so the common module has no hard
 * dependency on Fabric, Forge or NeoForge.
 */
public final class ModEnvironment {

    public static final String SOUND_PHYSICS_MOD_ID = "sound_physics_remastered";

    private static volatile Boolean soundPhysicsPresent;
    private static volatile Path configDirOverride;

    private ModEnvironment() {
    }

    /** Used by platforms without a loader config directory (the Bukkit plugin's data folder). */
    public static void setConfigDir(Path dir) {
        configDirOverride = dir;
    }

    public static Path configDir() {
        Path override = configDirOverride;
        if (override != null) {
            return override;
        }
        Path fabric = attempt(() -> {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            return (Path) loaderClass.getMethod("getConfigDir").invoke(loader);
        });
        if (fabric != null) {
            return fabric;
        }
        for (String paths : new String[]{"net.neoforged.fml.loading.FMLPaths", "net.minecraftforge.fml.loading.FMLPaths"}) {
            Path forge = attempt(() -> {
                Class<?> cls = Class.forName(paths);
                Object configDir = cls.getField("CONFIGDIR").get(null);
                return (Path) configDir.getClass().getMethod("get").invoke(configDir);
            });
            if (forge != null) {
                return forge;
            }
        }
        return Path.of("config");
    }

    public static boolean isModLoaded(String modId) {
        Boolean fabric = attempt(() -> {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            return (Boolean) loaderClass.getMethod("isModLoaded", String.class).invoke(loader, modId);
        });
        if (fabric != null) {
            return fabric;
        }
        for (String modList : new String[]{"net.neoforged.fml.ModList", "net.minecraftforge.fml.ModList"}) {
            Boolean forge = attempt(() -> {
                Class<?> cls = Class.forName(modList);
                Object list = cls.getMethod("get").invoke(null);
                return (Boolean) cls.getMethod("isLoaded", String.class).invoke(list, modId);
            });
            if (forge != null) {
                return forge;
            }
        }
        return false;
    }

    /**
     * Sound Physics Remastered applies its own occlusion to Simple Voice Chat. Running both would
     * muffle voices twice, so our wall engine stands down when it is installed.
     */
    public static boolean isSoundPhysicsPresent() {
        Boolean cached = soundPhysicsPresent;
        if (cached == null) {
            cached = isModLoaded(SOUND_PHYSICS_MOD_ID);
            soundPhysicsPresent = cached;
        }
        return cached;
    }

    private static <T> T attempt(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
