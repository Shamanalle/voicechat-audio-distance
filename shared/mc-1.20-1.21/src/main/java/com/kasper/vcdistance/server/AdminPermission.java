package com.kasper.vcdistance.server;

import com.kasper.vcdistance.OpsFile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who may use {@code /vcd} on 1.20 - 1.21.x: the console and command blocks, and operators of
 * level 2 or more. {@code CommandSourceStack.hasPermission(int)} is gone from the newest 1.21
 * releases, which moved to permission sets; there the level comes from {@code ops.json}.
 */
public final class AdminPermission {

    private static final OpsFile OPS = new OpsFile(FabricLoader.getInstance().getGameDir().resolve("ops.json"));
    private static boolean legacyMissing;

    private AdminPermission() {
    }

    public static boolean isAdmin(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return true;
        }
        if (!legacyMissing) {
            try {
                return source.hasPermission(2);
            } catch (NoSuchMethodError e) {
                legacyMissing = true;
            }
        }
        return OPS.level(player.getUUID()) >= 2;
    }
}
