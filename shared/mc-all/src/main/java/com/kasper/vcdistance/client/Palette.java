package com.kasper.vcdistance.client;

/**
 * Colors of the settings screen (ARGB). Minecraft 1.21.6+ skips text drawn with zero alpha,
 * so every color carries an explicit alpha channel.
 */
public final class Palette {

    public static final int TEXT = 0xFFE8EDF2;
    public static final int TEXT_DIM = 0xFFA3ADB8;
    public static final int TEXT_MUTED = 0xFF6F7A86;

    public static final int PANEL = 0xB0101418;
    public static final int PANEL_BORDER = 0xFF2B333D;
    public static final int GRID = 0x22FFFFFF;

    public static final int ACCENT = 0xFF4FD1C5;
    public static final int ACCENT_LINE = 0xFF8CEFE6;
    public static final int ACCENT_AREA = 0x554FD1C5;
    public static final int ACCENT_ZONE = 0x184FD1C5;

    public static final int FLOOR = 0xFFF6C453;

    // State colors: the usual set, or one told apart with red-green color blindness (see useColorblind)
    public static int WHISPER = 0xFFB794F6;
    public static int WARN = 0xFFF6C453;
    public static int GOOD = 0xFF68D391;
    public static int MUFFLED = 0xFFF6995C;
    public static int BAD = 0xFFF56565;

    public static final int BADGE = 0xF0181D24;
    public static final int BADGE_BORDER = 0xFF4A5563;

    private Palette() {
    }

    /**
     * Switches the state colors. The colorblind set follows Okabe and Ito: blue for talking,
     * reddish purple for whispers, orange for walls, yellow and vermilion for problems.
     */
    public static void useColorblind(boolean on) {
        if (on) {
            GOOD = 0xFF56B4E9;
            WHISPER = 0xFFCC79A7;
            MUFFLED = 0xFFE69F00;
            WARN = 0xFFF0E442;
            BAD = 0xFFD55E00;
        } else {
            GOOD = 0xFF68D391;
            WHISPER = 0xFFB794F6;
            MUFFLED = 0xFFF6995C;
            WARN = 0xFFF6C453;
            BAD = 0xFFF56565;
        }
    }

    /** Linear blend between two ARGB colors. */
    public static int mix(int a, int b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int oa = (int) Math.round(aa + (ba - aa) * t);
        int or = (int) Math.round(ar + (br - ar) * t);
        int og = (int) Math.round(ag + (bg - ag) * t);
        int ob = (int) Math.round(ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    public static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
