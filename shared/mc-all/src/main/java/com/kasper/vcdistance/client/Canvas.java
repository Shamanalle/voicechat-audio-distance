package com.kasper.vcdistance.client;

import net.minecraft.network.chat.Component;

/**
 * Minimal drawing surface for the settings screen.
 * <p>
 * Minecraft's GUI drawing API changes between versions (method return types in 1.21.6,
 * a new render-state API in 26.x), so the screen draws only through these four operations and
 * every version provides a small adapter. Colors are always ARGB with an explicit alpha.
 */
public interface Canvas {

    void fill(int x1, int y1, int x2, int y2, int argb);

    /** Left-aligned text with shadow. */
    void text(Component text, int x, int y, int argb);

    /** Centered text with shadow. */
    void centered(Component text, int centerX, int y, int argb);

    int width(Component text);

    default void right(Component text, int rightX, int y, int argb) {
        text(text, rightX - width(text), y, argb);
    }

    default void frame(int x1, int y1, int x2, int y2, int fillArgb, int borderArgb) {
        fill(x1, y1, x2, y2, fillArgb);
        fill(x1, y1, x2, y1 + 1, borderArgb);
        fill(x1, y2 - 1, x2, y2, borderArgb);
        fill(x1, y1 + 1, x1 + 1, y2 - 1, borderArgb);
        fill(x2 - 1, y1 + 1, x2, y2 - 1, borderArgb);
    }

    default void hLine(int x1, int x2, int y, int argb) {
        fill(x1, y, x2, y + 1, argb);
    }

    default void vLine(int x, int y1, int y2, int argb) {
        fill(x, y1, x + 1, y2, argb);
    }

    /**
     * Scales what is drawn until {@link #popScale}.
     *
     * @return {@code false} when this canvas cannot scale (then do not call popScale)
     */
    default boolean pushScale(float factor) {
        return false;
    }

    default void popScale() {
    }

    /** Dashed horizontal line: {@code dash} pixels on, {@code dash} off. */
    default void dashedHLine(int x1, int x2, int y, int dash, int argb) {
        for (int x = x1; x < x2; x += dash * 2) {
            fill(x, y, Math.min(x + dash, x2), y + 1, argb);
        }
    }

    /** Dashed vertical line: {@code dash} pixels on, {@code dash} off. */
    default void dashedVLine(int x, int y1, int y2, int dash, int argb) {
        for (int y = y1; y < y2; y += dash * 2) {
            fill(x, y, x + 1, Math.min(y + dash, y2), argb);
        }
    }
}
