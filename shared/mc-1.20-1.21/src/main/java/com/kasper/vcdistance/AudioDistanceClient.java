package com.kasper.vcdistance;

import com.kasper.vcdistance.client.ClientHints;
import com.kasper.vcdistance.client.HudHook;
import com.kasper.vcdistance.client.KeyMappings;
import com.kasper.vcdistance.client.MinecraftWorldAccess;
import com.kasper.vcdistance.client.SpeakerTicker;
import com.kasper.vcdistance.client.SvcSettingsButton;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AudioDistanceClient implements ClientModInitializer {

    public static KeyMapping OPEN_SETTINGS_KEY;
    public static KeyMapping TOGGLE_HUD_KEY;
    private static SpeakerTicker ticker;
    private static Object lastConnection;
    private static boolean helloSent;

    @Override
    public void onInitializeClient() {
        AudioDistancePlugin.CONFIG.ensureLoaded();
        ticker = new SpeakerTicker(new MinecraftWorldAccess());
        ModClientNetworking.register();

        try {
            OPEN_SETTINGS_KEY = KeyMappings.create("key.vc-audio-distance.open_settings", "key.categories.vc-audio-distance");
            if (OPEN_SETTINGS_KEY != null) {
                KeyBindingHelper.registerKeyBinding(OPEN_SETTINGS_KEY);
            }
            TOGGLE_HUD_KEY = KeyMappings.create("key.vc-audio-distance.toggle_hud", "key.categories.vc-audio-distance");
            if (TOGGLE_HUD_KEY != null) {
                KeyBindingHelper.registerKeyBinding(TOGGLE_HUD_KEY);
            }
        } catch (Throwable t) {
            OPEN_SETTINGS_KEY = null;
            TOGGLE_HUD_KEY = null;
            DistanceConfig.LOGGER.warn("Could not register the keys; use Mod Menu or the Voice Chat settings instead: {}", t.toString());
        }
        try {
            HudHook.register();
        } catch (Throwable t) {
            DistanceConfig.LOGGER.warn("Could not add the voice HUD: {}", t.toString());
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticker.tick();
            tickServerLink(client);
            if (OPEN_SETTINGS_KEY != null) {
                while (OPEN_SETTINGS_KEY.consumeClick()) {
                    client.setScreen(new AudioDistanceScreen(client.screen));
                }
            }
            if (TOGGLE_HUD_KEY != null) {
                while (TOGGLE_HUD_KEY.consumeClick()) {
                    ClientHints.cycleHud();
                }
            }
            ClientHints.tickZoneNotice();
            ClientHints.tickWelcome(client.player != null && client.level != null, OPEN_SETTINGS_KEY,
                    message -> client.player.displayClientMessage(message, false));
        });
        ScreenEvents.AFTER_INIT.register(AudioDistanceClient::onScreenInit);
    }

    /** Says hello to servers that have the addon and announces their profile once. */
    private static void tickServerLink(Minecraft client) {
        try {
            Object connection = client.getConnection();
            if (connection != lastConnection) {
                lastConnection = connection;
                helloSent = false;
                AudioDistancePlugin.LINK.reset();
            }
            if (connection != null && !helloSent) {
                helloSent = ModClientNetworking.trySendHello(BuildInfo.version());
            }
            if (client.player != null && AudioDistancePlugin.LINK.consumeNotice()) {
                String mode = AudioDistancePlugin.LINK.isEnforced() ? "enforce" : "suggest";
                client.player.displayClientMessage(Component.translatable("message.vc-audio-distance.server_profile." + mode), false);
            }
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Server link tick failed: {}", t.toString());
        }
    }

    /** Adds a button to Simple Voice Chat's own settings screen. */
    private static void onScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!SvcSettingsButton.isSvcSettings(screen)) {
            return;
        }
        Screens.getButtons(screen).add(Button.builder(
                Component.translatable("message.vc-audio-distance.button"),
                button -> client.setScreen(new AudioDistanceScreen(screen))
        ).bounds(SvcSettingsButton.x(scaledWidth), SvcSettingsButton.y(scaledHeight),
                SvcSettingsButton.WIDTH, SvcSettingsButton.HEIGHT).build());
    }
}
