package com.kasper.vcdistance.server;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.Zone;
import net.minecraft.server.level.ServerPlayer;


/**
 * Sound zones on a Fabric or NeoForge server: worlds are dimensions ("minecraft:the_nether", also
 * matched by "the_nether") and boxes; there are no WorldGuard regions here.
 */
public final class ServerZones {

    private ServerZones() {
    }

    /** The zone a player is in, or {@code null} for the server's main profile. */
    public static Zone of(ServerPlayer player) {
        if (AudioDistancePlugin.SERVER_SETTINGS.zones().isEmpty()) {
            return null;
        }
        return ServerBridge.zoneOf(player);
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
