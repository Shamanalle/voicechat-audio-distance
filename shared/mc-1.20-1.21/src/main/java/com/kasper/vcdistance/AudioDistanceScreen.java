package com.kasper.vcdistance;

import com.kasper.vcdistance.client.Compat;
import com.kasper.vcdistance.client.GuiCanvas;
import com.kasper.vcdistance.client.SettingsScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

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
    protected void playPreview(float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_AMBIENT, 1.0F, volume));
    }

    @Override
    protected boolean inWorld() {
        return this.minecraft != null && this.minecraft.level != null;
    }

    @Override
    protected String readClipboard() {
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        return text == null ? "" : text;
    }

    @Override
    protected void writeClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Compat.renderBackground(this, graphics);
        super.render(graphics, mouseX, mouseY, delta);
        paint(new GuiCanvas(graphics, this.font), mouseX, mouseY);
    }
}
