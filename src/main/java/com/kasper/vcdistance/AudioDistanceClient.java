package com.kasper.vcdistance;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class AudioDistanceClient implements ClientModInitializer {

    public static KeyMapping OPEN_SETTINGS_KEY;

    @Override
    public void onInitializeClient() {
        // 1. Register configurable Keybinding in Minecraft controls
        OPEN_SETTINGS_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.vc-audio-distance.open_settings",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.vc-audio-distance"
        ));

        // 2. Listen for key presses to open settings
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_SETTINGS_KEY.consumeClick()) {
                client.setScreen(new AudioDistanceScreen(client.screen));
            }
        });

        // 3. Inject button into Simple Voice Chat settings screen
        ScreenEvents.AFTER_INIT.register(this::onScreenInit);
    }

    private void onScreenInit(net.minecraft.client.Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        // Check by class name to avoid requiring SVC classes on compile classpath
        if (!screen.getClass().getName().equals("de.maxhenkel.voicechat.gui.VoiceChatSettingsScreen")) {
            return;
        }

        int x = (scaledWidth - 200) / 2;
        // Place button just below the SVC panel (panel is 219px tall, centered)
        int panelBottom = (scaledHeight + 219) / 2;
        int y = panelBottom + 3;

        Screens.getButtons(screen).add(Button.builder(
                Component.translatable("message.vc-audio-distance.button"),
                button -> client.setScreen(new AudioDistanceScreen(screen))
        ).bounds(x, y, 200, 20).build());
    }
}
