package com.kasper.vcdistance;

import com.kasper.vcdistance.client.ExtractorCanvas;
import com.kasper.vcdistance.client.SettingsScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * Settings screen for Minecraft 26.x. All logic lives in {@link SettingsScreen}.
 */
public class AudioDistanceScreen extends SettingsScreen {

    public AudioDistanceScreen(Screen parent) {
        super(parent);
    }

    @Override
    protected void openScreen(Screen screen) {
        if (this.minecraft != null && this.minecraft.gui != null) {
            this.minecraft.gui.setScreen(screen);
        }
    }

    @Override
    protected boolean inWorld() {
        return this.minecraft != null && this.minecraft.level != null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        paint(new ExtractorCanvas(graphics, this.font), mouseX, mouseY);
    }
}
