package com.kasper.vcdistance;

import com.kasper.vcdistance.client.Compat;
import com.kasper.vcdistance.client.GuiCanvas;
import com.kasper.vcdistance.client.SettingsScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Settings screen for Minecraft 1.20 - 1.21.x. All logic lives in {@link SettingsScreen}.
 */
public class AudioDistanceScreen extends SettingsScreen {

    public AudioDistanceScreen(Screen parent) {
        super(parent);
    }

    @Override
    protected void openScreen(Screen screen) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(screen);
        }
    }

    @Override
    protected boolean inWorld() {
        return this.minecraft != null && this.minecraft.level != null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Compat.renderBackground(this, graphics);
        super.render(graphics, mouseX, mouseY, delta);
        paint(new GuiCanvas(graphics, this.font), mouseX, mouseY);
    }
}
