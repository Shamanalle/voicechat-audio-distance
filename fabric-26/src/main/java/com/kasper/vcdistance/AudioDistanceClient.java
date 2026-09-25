package com.kasper.vcdistance;

import com.kasper.vcdistance.client.ModernWorldAccess;
import com.kasper.vcdistance.client.ScreenSwitch;
import com.kasper.vcdistance.client.SpeakerTicker;
import com.kasper.vcdistance.client.SvcSettingsButton;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Client entrypoint for Minecraft 26.x (KeyMapping categories). One jar serves every 26.x release:
 * the unbound key comes from {@link InputConstants#UNKNOWN} at runtime (GLFW before 26.3, SDL3 after),
 * and screens are opened through {@link ScreenSwitch}.
 */
@Environment(EnvType.CLIENT)
public class AudioDistanceClient implements ClientModInitializer {

    public static KeyMapping.Category CATEGORY;
    public static KeyMapping OPEN_SETTINGS_KEY;
    private static SpeakerTicker ticker;
    private static Object lastConnection;
    private static boolean helloSent;

    @Override
    public void onInitializeClient() {
        AudioDistancePlugin.CONFIG.ensureLoaded();
        ticker = new SpeakerTicker(new ModernWorldAccess());
        ModClientNetworking.register();

        CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("vc-audio-distance", "general"));
        OPEN_SETTINGS_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.vc-audio-distance.open_settings",
                InputConstants.UNKNOWN.getValue(),
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticker.tick();
            tickServerLink(client);
            while (OPEN_SETTINGS_KEY.consumeClick()) {
                ScreenSwitch.open(client, new AudioDistanceScreen(ScreenSwitch.current(client)));
            }
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
                client.player.sendSystemMessage(Component.translatable("message.vc-audio-distance.server_profile." + mode));
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
        Screens.getWidgets(screen).add(Button.builder(
                Component.translatable("message.vc-audio-distance.button"),
                button -> ScreenSwitch.open(client, new AudioDistanceScreen(screen))
        ).bounds(SvcSettingsButton.x(scaledWidth), SvcSettingsButton.y(scaledHeight),
                SvcSettingsButton.WIDTH, SvcSettingsButton.HEIGHT).build());
    }
}
