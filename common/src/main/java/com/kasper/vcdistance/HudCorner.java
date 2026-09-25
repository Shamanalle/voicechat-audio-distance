package com.kasper.vcdistance;

import java.util.Locale;

/** Screen corner of the voice HUD. */
public enum HudCorner {
    // Clockwise, so the corner button walks round the screen
    TOP_LEFT("top_left", false, false, "↖"),
    TOP_RIGHT("top_right", true, false, "↗"),
    BOTTOM_RIGHT("bottom_right", true, true, "↘"),
    BOTTOM_LEFT("bottom_left", false, true, "↙");

    private final String id;
    private final boolean right;
    private final boolean bottom;
    private final String arrow;

    HudCorner(String id, boolean right, boolean bottom, String arrow) {
        this.id = id;
        this.right = right;
        this.bottom = bottom;
        this.arrow = arrow;
    }

    /** An arrow pointing into this corner, for short labels. */
    public String getArrow() {
        return arrow;
    }

    public String getId() {
        return id;
    }

    public boolean isRight() {
        return right;
    }

    public boolean isBottom() {
        return bottom;
    }

    public String getTranslationKey() {
        return "gui.vc-audio-distance.hud.corner." + id;
    }

    public HudCorner next() {
        HudCorner[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static HudCorner fromId(String id, HudCorner fallback) {
        if (id != null) {
            for (HudCorner c : values()) {
                if (c.id.equals(id.trim().toLowerCase(Locale.ROOT))) {
                    return c;
                }
            }
        }
        return fallback;
    }
}
