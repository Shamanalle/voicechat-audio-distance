package com.kasper.vcdistance;

import java.util.Locale;

/** When the in-game voice HUD is shown. */
public enum HudMode {
    /** Never. */
    OFF("off"),
    /** While someone nearby talks or you talk. */
    TALKING("talking"),
    /** Always while in a world: also how many players are within voice range. */
    ALWAYS("always");

    private final String id;

    HudMode(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getTranslationKey() {
        return "gui.vc-audio-distance.hud.mode." + id;
    }

    public HudMode next() {
        HudMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static HudMode fromId(String id, HudMode fallback) {
        if (id != null) {
            for (HudMode m : values()) {
                if (m.id.equals(id.trim().toLowerCase(Locale.ROOT))) {
                    return m;
                }
            }
        }
        return fallback;
    }
}
