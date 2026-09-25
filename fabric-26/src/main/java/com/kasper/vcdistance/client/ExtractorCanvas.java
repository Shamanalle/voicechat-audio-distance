package com.kasper.vcdistance.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * {@link Canvas} over the 26.x render-state API.
 */
public final class ExtractorCanvas implements Canvas {

    private final GuiGraphicsExtractor graphics;
    private final Font font;

    public ExtractorCanvas(GuiGraphicsExtractor graphics, Font font) {
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
        graphics.text(font, text, x, y, argb, true);
    }

    @Override
    public void centered(Component text, int centerX, int y, int argb) {
        graphics.centeredText(font, text, centerX, y, argb);
    }

    @Override
    public int width(Component text) {
        return font.width(text);
    }
}
