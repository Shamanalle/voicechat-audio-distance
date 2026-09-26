package com.kasper.vcdistance.server;

import net.minecraft.server.level.ServerPlayer;

/** A player's game language; Minecraft 1.20.1 does not expose it, so replies use the configured one. */
public final class PlayerLanguage {

    private PlayerLanguage() {
    }

    public static String of(ServerPlayer player) {
        return "";
    }
}
