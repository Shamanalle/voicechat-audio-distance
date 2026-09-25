package com.kasper.vcdistance.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;

/**
 * Draws the voice HUD on Minecraft 1.20 - 1.21.x. {@link HudRenderCallback} exists on every one of
 * them (its second parameter is a {@code float} on 1.20 and a {@code DeltaTracker} on 1.21, which
 * the lambda does not use), so the same source serves both jars.
 */
public final class HudHook {

    private HudHook() {
    }

    @SuppressWarnings("deprecation")
    public static void register() {
        HudRenderCallback.EVENT.register((graphics, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            HudOverlay.paint(new GuiCanvas(graphics, mc.font), graphics.guiWidth(), graphics.guiHeight(),
                    mc.level != null && mc.player != null, mc.options.hideGui);
        });
    }
}
