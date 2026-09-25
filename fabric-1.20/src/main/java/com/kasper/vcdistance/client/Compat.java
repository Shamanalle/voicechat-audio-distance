package com.kasper.vcdistance.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Minecraft 1.20.1 specifics.
 */
public final class Compat {

    private Compat() {
    }

    /** 1.20.1 screens draw their own background before the widgets. */
    public static void renderBackground(Screen screen, GuiGraphics graphics) {
        screen.renderBackground(graphics);
    }
}
