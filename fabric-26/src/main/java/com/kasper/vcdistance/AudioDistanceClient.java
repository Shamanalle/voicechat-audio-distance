package com.kasper.vcdistance;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.sdl.SDLScancode;

/**
 * Client mod initializer for Minecraft 26.3.
 * Handles keybindings (via SDL3 and KeyMappingHelper) and GUI opening.
 */
@Environment(EnvType.CLIENT)
public class AudioDistanceClient implements ClientModInitializer {

    public static KeyMapping.Category CATEGORY;
    public static KeyMapping OPEN_SETTINGS_KEY;

    @Override
    public void onInitializeClient() {
        CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("vc-audio-distance", "general"));

        OPEN_SETTINGS_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.vc-audio-distance.settings",
                SDLScancode.SDL_SCANCODE_UNKNOWN,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_SETTINGS_KEY.consumeClick()) {
                if (client.gui != null) {
                    client.gui.setScreen(new AudioDistanceScreen(client.gui.screen()));
                }
            }
        });
    }
}
