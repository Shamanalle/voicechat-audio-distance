package com.kasper.vcdistance.neoforge;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.AudioDistanceScreen;
import com.kasper.vcdistance.BuildInfo;
import com.kasper.vcdistance.DistanceConfig;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.client.ClientHints;
import com.kasper.vcdistance.client.ExtractorCanvas;
import com.kasper.vcdistance.client.HudOverlay;
import com.kasper.vcdistance.client.ModernWorldAccess;
import com.kasper.vcdistance.client.ScreenSwitch;
import com.kasper.vcdistance.client.SpeakerTicker;
import com.kasper.vcdistance.client.SvcSettingsButton;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client half on NeoForge: the same ticker, settings screen, HUD and keys as the Fabric build,
 * hooked in through NeoForge's events.
 */
final class NeoClient {

    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath("vc-audio-distance", "general"));
    private static final KeyMapping OPEN_SETTINGS_KEY =
            new KeyMapping("key.vc-audio-distance.open_settings", InputConstants.UNKNOWN.getValue(), CATEGORY);
    private static final KeyMapping TOGGLE_HUD_KEY =
            new KeyMapping("key.vc-audio-distance.toggle_hud", InputConstants.UNKNOWN.getValue(), CATEGORY);

    private static SpeakerTicker ticker;
    private static Object lastConnection;
    private static boolean helloSent;

    private NeoClient() {
    }

    static void init(IEventBus modBus, ModContainer container) {
        AudioDistancePlugin.CONFIG.ensureLoaded();
        ticker = new SpeakerTicker(new ModernWorldAccess());
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (mod, parent) -> new AudioDistanceScreen(parent));

        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.registerCategory(CATEGORY);
            e.register(OPEN_SETTINGS_KEY);
            e.register(TOGGLE_HUD_KEY);
        });
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAboveAll(
                Identifier.fromNamespaceAndPath("vc-audio-distance", "voice_hud"), (graphics, delta) -> {
                    Minecraft mc = Minecraft.getInstance();
                    HudOverlay.paint(new ExtractorCanvas(graphics, mc.font), graphics.guiWidth(), graphics.guiHeight(),
                            mc.level != null && mc.player != null, false);
                }));

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post e) -> {
            if (!SvcSettingsButton.isSvcSettings(e.getScreen())) {
                return;
            }
            Minecraft client = Minecraft.getInstance();
            e.addListener(Button.builder(Component.translatable("message.vc-audio-distance.button"),
                            b -> ScreenSwitch.open(client, new AudioDistanceScreen(e.getScreen())))
                    .bounds(SvcSettingsButton.x(e.getScreen().width), SvcSettingsButton.y(e.getScreen().height),
                            SvcSettingsButton.WIDTH, SvcSettingsButton.HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("message.vc-audio-distance.button.tooltip"))).build());
        });
    }

    private static void tick(Minecraft client) {
        ticker.tick();
        tickServerLink(client);
        while (OPEN_SETTINGS_KEY.consumeClick()) {
            ScreenSwitch.open(client, new AudioDistanceScreen(ScreenSwitch.current(client)));
        }
        while (TOGGLE_HUD_KEY.consumeClick()) {
            ClientHints.cycleHud();
        }
        ClientHints.tickZoneNotice();
        ClientHints.tickWelcome(client.player != null && client.level != null, OPEN_SETTINGS_KEY,
                message -> client.player.sendSystemMessage(message));
    }

    /** Says hello to servers that have the addon and announces their profile once. */
    private static void tickServerLink(Minecraft client) {
        try {
            ClientPacketListener connection = client.getConnection();
            if (connection != lastConnection) {
                lastConnection = connection;
                helloSent = false;
                AudioDistancePlugin.LINK.reset();
            }
            if (connection != null && !helloSent && connection.hasChannel(NeoNetworking.Hello.TYPE)) {
                ClientPacketDistributor.sendToServer(new NeoNetworking.Hello(LinkProtocol.hello(BuildInfo.version())));
                helloSent = true;
            }
            if (client.player != null && AudioDistancePlugin.LINK.consumeNotice()) {
                String mode = AudioDistancePlugin.LINK.isEnforced() ? "enforce" : "suggest";
                client.player.sendSystemMessage(Component.translatable("message.vc-audio-distance.server_profile." + mode));
            }
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Server link tick failed: {}", t.toString());
        }
    }
}
