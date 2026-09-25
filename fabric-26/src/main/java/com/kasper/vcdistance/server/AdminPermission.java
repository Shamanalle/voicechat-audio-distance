package com.kasper.vcdistance.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

/** Who may use {@code /vcd} on 26.x: the console and command blocks, and game masters (op level 2+). */
public final class AdminPermission {

    private AdminPermission() {
    }

    public static boolean isAdmin(CommandSourceStack source) {
        return source.getPlayer() == null || source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
}
