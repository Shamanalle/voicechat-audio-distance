package com.kasper.vcdistance.server;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.Zone;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Sound zones on a Fabric server: worlds are dimensions ("minecraft:the_nether", also matched by
 * "the_nether"). There are no WorldGuard regions on Fabric.
 */
public final class ServerZones {

    private ServerZones() {
    }

    /** The zone a player is in, or {@code null} for the server's main profile. */
    public static Zone of(ServerPlayer player) {
        var zones = AudioDistancePlugin.SERVER_SETTINGS.zones();
        if (zones.isEmpty()) {
            return null;
        }
        return Zone.resolve(zones, dimensionId(String.valueOf(player.level().dimension())), List.of());
    }

    /**
     * The dimension id from a ResourceKey's text, "ResourceKey[minecraft:dimension / minecraft:the_nether]".
     * Reading the text avoids the accessor, which was renamed between Minecraft versions.
     */
    static String dimensionId(String key) {
        int slash = key.lastIndexOf(" / ");
        int end = key.lastIndexOf(']');
        if (slash >= 0 && end > slash) {
            return key.substring(slash + 3, end).trim();
        }
        return key;
    }
}
