package com.kasper.vcdistance.client;

import net.minecraft.client.gui.screens.Screen;

/**
 * Placement of the "Voice distance" button inside Simple Voice Chat's own settings screen.
 */
public final class SvcSettingsButton {

    /** Compared by name so SVC classes are not needed at compile time. */
    private static final String SVC_SETTINGS_SCREEN = "de.maxhenkel.voicechat.gui.VoiceChatSettingsScreen";
    /** Size of SVC's settings panel (VoiceChatSettingsScreen: 248 x 219). */
    private static final int PANEL_HEIGHT = 219;

    public static final int WIDTH = 200;
    public static final int HEIGHT = 20;

    private SvcSettingsButton() {
    }

    public static boolean isSvcSettings(Screen screen) {
        return screen != null && SVC_SETTINGS_SCREEN.equals(screen.getClass().getName());
    }

    public static int x(int scaledWidth) {
        return (scaledWidth - WIDTH) / 2;
    }

    /** Below the panel when there is room, otherwise above it. */
    public static int y(int scaledHeight) {
        int panelBottom = (scaledHeight + PANEL_HEIGHT) / 2;
        return scaledHeight - panelBottom >= HEIGHT + 5
                ? panelBottom + 3
                : Math.max(3, (scaledHeight - PANEL_HEIGHT) / 2 - HEIGHT - 3);
    }
}
