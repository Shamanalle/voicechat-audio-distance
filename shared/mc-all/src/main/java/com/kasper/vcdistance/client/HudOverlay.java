package com.kasper.vcdistance.client;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.Bearing;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.HearingEstimate;
import com.kasper.vcdistance.HudCorner;
import com.kasper.vcdistance.HudMode;
import com.kasper.vcdistance.NearbyPlayers;
import com.kasper.vcdistance.SpeakerRegistry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The in-game voice HUD: who is talking nearby (distance, direction, whisper, behind a wall) and,
 * while you talk, how many players within your range will hear you. Drawn through a {@link Canvas}
 * by each version's HUD hook; never throws.
 */
public final class HudOverlay {

    private static final String K = "gui.vc-audio-distance.hud.";
    private static final int MAX_TALKERS = 4;
    private static final int MARGIN = 4;
    private static final int PAD = 4;
    private static final int LINE = 11;
    /** The "hear you" line stays this long after you stop talking. */
    private static final long SELF_LINGER_NANOS = TimeUnit.MILLISECONDS.toNanos(1500);

    /** A short notice (e.g. after the HUD key), shown even when the HUD is off. */
    private static final long FLASH_NANOS = TimeUnit.MILLISECONDS.toNanos(2000);
    private static volatile Component flash;
    private static volatile long flashNanos;

    private static long lastSelfTalkNanos = Long.MIN_VALUE;
    private static boolean lastSelfWhisper;
    private static boolean loggedFailure;

    private record Line(Component text, int dotColor, int textColor) {
    }

    private HudOverlay() {
    }

    /**
     * @param inWorld   {@code false} on the title screen and while loading
     * @param hidden    F1 or another reason to draw no HUD
     */
    public static void paint(Canvas c, int screenW, int screenH, boolean inWorld, boolean hidden) {
        try {
            if (inWorld && !hidden) {
                paintUnsafe(c, screenW, screenH);
            }
        } catch (Throwable t) {
            if (!loggedFailure) {
                loggedFailure = true;
                DistanceConfig.LOGGER.warn("Voice HUD failed, it stays hidden: {}", t.toString());
            }
        }
    }

    /** Shows {@code text} in the HUD's corner for two seconds. */
    public static void flash(Component text) {
        flashNanos = System.nanoTime();
        flash = text;
    }

    private static void paintUnsafe(Canvas c, int screenW, int screenH) {
        DistanceConfig prefs = AudioDistancePlugin.CONFIG;
        HudMode mode = prefs.getHudMode();
        long now = System.nanoTime();
        List<Line> lines = new ArrayList<>();
        Component notice = flash;
        if (notice != null && now - flashNanos <= FLASH_NANOS) {
            lines.add(new Line(notice, 0, Palette.ACCENT_LINE));
        }
        if (mode == HudMode.OFF) {
            draw(c, lines, screenW, screenH, prefs);
            return;
        }

        // Voices being heard
        List<SpeakerRegistry.Speaker> talkers = AudioDistancePlugin.SPEAKERS.active(now);
        int shown = 0;
        boolean wallsActive = AudioDistancePlugin.occlusionStatus() == AudioDistancePlugin.OcclusionStatus.ACTIVE;
        for (SpeakerRegistry.Speaker s : talkers) {
            if (shown == MAX_TALKERS) {
                lines.add(new Line(hud("more", talkers.size() - shown), 0, Palette.TEXT_MUTED));
                break;
            }
            lines.add(talkerLine(s, wallsActive));
            shown++;
        }

        // You
        if (AudioDistancePlugin.isSelfTalking(now)) {
            lastSelfTalkNanos = now;
            lastSelfWhisper = AudioDistancePlugin.isSelfWhispering();
        }
        boolean selfRecent = lastSelfTalkNanos != Long.MIN_VALUE && now - lastSelfTalkNanos <= SELF_LINGER_NANOS;
        double voiceRange = AudioDistancePlugin.getServerMaxDistance();
        if (selfRecent) {
            double range = lastSelfWhisper ? voiceRange * AudioDistancePlugin.LINK.whisperShare() : voiceRange;
            HearingEstimate e = HearingEstimate.of(AudioDistancePlugin.NEARBY.players(), range,
                    id -> AudioDistancePlugin.voiceState(id, now));
            lines.add(hearingLine(e, lastSelfWhisper));
        } else if (mode == HudMode.ALWAYS && talkers.isEmpty()) {
            HearingEstimate e = HearingEstimate.of(AudioDistancePlugin.NEARBY.players(), voiceRange,
                    id -> AudioDistancePlugin.voiceState(id, now));
            lines.add(e.inRange() == 0
                    ? new Line(hud("nobody_near"), 0, Palette.TEXT_MUTED)
                    : new Line(hud("in_range", e.inRange()), 0, Palette.TEXT_DIM));
        }
        draw(c, lines, screenW, screenH, prefs);
    }

    private static void draw(Canvas c, List<Line> lines, int screenW, int screenH, DistanceConfig prefs) {
        if (lines.isEmpty()) {
            return;
        }
        int w = 0;
        for (Line l : lines) {
            w = Math.max(w, c.width(l.text()) + (l.dotColor() != 0 ? 7 : 0));
        }
        w += PAD * 2;
        int h = lines.size() * LINE + PAD * 2 - 2;
        HudCorner corner = prefs.getHudCorner();
        int x = corner.isRight() ? screenW - MARGIN - w : MARGIN;
        // Keep clear of the hotbar and chat at the bottom
        int y = corner.isBottom() ? screenH - MARGIN - h - 42 : MARGIN;
        c.frame(x, y, x + w, y + h, 0x90101418, 0x60FFFFFF);
        int ty = y + PAD;
        for (Line l : lines) {
            int tx = x + PAD;
            if (l.dotColor() != 0) {
                c.fill(tx, ty + 2, tx + 4, ty + 6, l.dotColor());
                tx += 7;
            }
            c.text(l.text(), tx, ty, l.textColor());
            ty += LINE;
        }
    }

    private static Line talkerLine(SpeakerRegistry.Speaker s, boolean wallsActive) {
        Component name = s.getDisplayName() != null && !s.getDisplayName().isEmpty()
                ? Component.literal(s.getDisplayName())
                : Component.translatable("gui.vc-audio-distance.monitor.source");
        StringBuilder where = new StringBuilder();
        if (s.getDistance() >= 0.0) {
            where.append(' ').append(Math.round(s.getDistance()));
        }
        String arrow = Bearing.arrow(s.getBearing());
        if (!arrow.isEmpty()) {
            where.append(' ').append(arrow);
        }
        Component text = Component.empty().append(name).append(Component.literal(where.toString()));
        int color = Palette.TEXT;
        if (s.isWhispering()) {
            text = Component.empty().append(text).append(Component.literal(" · ")).append(Component.translatable("gui.vc-audio-distance.monitor.whisper"));
            color = Palette.WHISPER;
        } else if (wallsActive && s.getFilter().getDisplayLossDb() > 1.0F) {
            text = Component.empty().append(text).append(Component.literal(" · ")).append(hud("walls"));
            color = Palette.MUFFLED;
        }
        boolean loud = s.getLevelDb() > -50.0F;
        return new Line(text, loud ? Palette.GOOD : Palette.withAlpha(Palette.TEXT_MUTED, 0xC0), color);
    }

    private static Line hearingLine(HearingEstimate e, boolean whisper) {
        String suffix = whisper ? "_whisper" : "";
        if (e.inRange() == 0) {
            return new Line(hud("nobody" + suffix), Palette.WARN, Palette.WARN);
        }
        if (e.unknown() == e.inRange()) {
            // Nobody's voice chat state is known: only how many are close enough
            return new Line(hud("in_range" + suffix, e.inRange()), Palette.ACCENT, Palette.TEXT);
        }
        Component count = e.isExact()
                ? Component.literal(String.valueOf(e.hear()))
                : hud("of", e.hear(), e.inRange());
        Component text = hud("hears" + suffix, count);
        if (e.deaf() > 0) {
            text = Component.empty().append(text).append(Component.literal(" · ")).append(hud("deaf", e.deaf()));
        }
        int color = e.hear() == 0 ? Palette.WARN : Palette.TEXT;
        return new Line(text, e.hear() == 0 ? Palette.WARN : Palette.GOOD, color);
    }

    private static Component hud(String key, Object... args) {
        return Component.translatable(K + key, args);
    }

    /** For the settings screen: the HUD mode label. */
    static Component modeLabel(HudMode mode) {
        return Component.translatable(K + "mode", Component.translatable(mode.getTranslationKey()));
    }

    static Component cornerLabel(HudCorner corner) {
        return Component.translatable(K + "corner", Component.translatable(corner.getTranslationKey()));
    }
}
