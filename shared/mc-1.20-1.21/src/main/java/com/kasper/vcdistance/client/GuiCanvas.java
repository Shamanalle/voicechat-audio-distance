package com.kasper.vcdistance.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * {@link Canvas} over {@link GuiGraphics} for Minecraft 1.20 - 1.21.x.
 * <p>
 * {@code GuiGraphics.drawString} returns {@code int} up to 1.21.5 and {@code void} since 1.21.6, so
 * a jar compiled against one of them crashes on the other. {@code drawCenteredString} and
 * {@code fill} kept the same signature across the whole range, so left-aligned text is drawn by
 * centering it on {@code x + width / 2}, which lands exactly on {@code x}.
 */
public final class GuiCanvas implements Canvas {

    private final GuiGraphics graphics;
    private final Font font;

    public GuiCanvas(GuiGraphics graphics, Font font) {
        this.graphics = graphics;
        this.font = font;
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        if (x2 > x1 && y2 > y1) {
            graphics.fill(x1, y1, x2, y2, argb);
        }
    }

    @Override
    public void text(Component text, int x, int y, int argb) {
        graphics.drawCenteredString(font, text, x + font.width(text) / 2, y, argb);
    }

    @Override
    public void centered(Component text, int centerX, int y, int argb) {
        graphics.drawCenteredString(font, text, centerX, y, argb);
    }

    @Override
    public int width(Component text) {
        return font.width(text);
    }
}
