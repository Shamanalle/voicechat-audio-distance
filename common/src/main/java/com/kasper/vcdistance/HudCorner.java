package com.kasper.vcdistance;

import java.util.Locale;

/** Screen corner of the voice HUD. */
public enum HudCorner {
    TOP_LEFT("top_left", false, false),
    TOP_RIGHT("top_right", true, false),
    BOTTOM_LEFT("bottom_left", false, true),
    BOTTOM_RIGHT("bottom_right", true, true);

    private final String id;
    private final boolean right;
    private final boolean bottom;

    HudCorner(String id, boolean right, boolean bottom) {
        this.id = id;
        this.right = right;
        this.bottom = bottom;
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
