package com.kasper.vcdistance.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Draws the voice HUD on Minecraft 26.x, as a HUD element after the vanilla ones (the whole HUD,
 * this element included, is skipped while the GUI is hidden with F1).
 */
public final class HudHook {

    private HudHook() {
    }

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("vc-audio-distance", "voice_hud"), (graphics, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            HudOverlay.paint(new ExtractorCanvas(graphics, mc.font), graphics.guiWidth(), graphics.guiHeight(),
                    mc.level != null && mc.player != null, false);
        });
    }
}
