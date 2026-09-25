package com.kasper.vcdistance.bukkit;

import com.kasper.vcdistance.DistanceConfig;
import org.bukkit.Location;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * The WorldGuard regions at a location, highest priority first, looked up by reflection so the
 * plugin neither needs nor ships WorldGuard. Without WorldGuard (or on an API it does not know) it
 * returns no regions and zones fall back to worlds.
 */
final class WorldGuardRegions {

    private static boolean initialized;
    private static Object query;
    private static Method adapt;
    private static Method applicable;
    private static Method getId;
    private static Method getPriority;

    private WorldGuardRegions() {
    }

    static List<String> at(Location location) {
        if (!init()) {
            return List.of();
        }
        try {
            Object weLocation = adapt.invoke(null, location);
            Iterable<?> set = (Iterable<?>) applicable.invoke(query, weLocation);
            List<Object[]> found = new ArrayList<>();
            for (Object region : set) {
                found.add(new Object[]{getId.invoke(region), getPriority.invoke(region)});
            }
            found.sort((a, b) -> Integer.compare((Integer) b[1], (Integer) a[1]));
            List<String> ids = new ArrayList<>(found.size());
            for (Object[] f : found) {
                ids.add((String) f[0]);
            }
            return ids;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
    }

    private static synchronized boolean init() {
        if (initialized) {
            return query != null;
        }
        initialized = true;
        try {
            Class<?> worldGuard = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object instance = worldGuard.getMethod("getInstance").invoke(null);
            Object platform = worldGuard.getMethod("getPlatform").invoke(instance);
            Class<?> platformType = Class.forName("com.sk89q.worldguard.internal.platform.WorldGuardPlatform");
            Object container = platformType.getMethod("getRegionContainer").invoke(platform);
            Class<?> containerType = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer");
            Object q = containerType.getMethod("createQuery").invoke(container);
            Class<?> queryType = Class.forName("com.sk89q.worldguard.protection.regions.RegionQuery");
            Class<?> weLocation = Class.forName("com.sk89q.worldedit.util.Location");
            adapt = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter").getMethod("adapt", Location.class);
            applicable = queryType.getMethod("getApplicableRegions", weLocation);
            Class<?> region = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion");
            getId = region.getMethod("getId");
            getPriority = region.getMethod("getPriority");
            query = q;
            DistanceConfig.LOGGER.info("WorldGuard found: zone.region.* settings are active");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            DistanceConfig.LOGGER.warn("WorldGuard is installed but could not be read, region zones are off: {}", e.toString());
            return false;
        }
    }
}
