package com.kasper.vcdistance.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Minecraft 1.20.2 - 1.20.4 specifics.
 */
public final class Compat {

    private Compat() {
    }

    /** Since 1.20.2 {@code Screen.render} draws the background (with blur) itself. */
    public static void renderBackground(Screen screen, GuiGraphics graphics) {
    }
}
