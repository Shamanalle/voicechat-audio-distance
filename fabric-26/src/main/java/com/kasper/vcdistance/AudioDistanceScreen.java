package com.kasper.vcdistance;

import com.kasper.vcdistance.client.ExtractorCanvas;
import com.kasper.vcdistance.client.ScreenSwitch;
import com.kasper.vcdistance.client.SettingsScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Settings screen for Minecraft 26.x. All logic lives in {@link SettingsScreen}.
 */
public class AudioDistanceScreen extends SettingsScreen {

    public AudioDistanceScreen(Screen parent) {
        super(parent);
    }

    @Override
    protected void openScreen(Screen screen) {
        if (this.minecraft != null) {
            ScreenSwitch.open(this.minecraft, screen);
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        paint(new ExtractorCanvas(graphics, this.font), mouseX, mouseY);
    }
}
