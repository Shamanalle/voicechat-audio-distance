package com.kasper.vcdistance.client;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.DistanceConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Small client behaviours shared by every version: the first-join hint and the HUD toggle key.
 */
public final class ClientHints {

    /** Wait this long in the world before the hint, so it is not lost among the join messages. */
    private static final int WELCOME_DELAY_TICKS = 100;

    private static int ticksInWorld;

    private ClientHints() {
    }

    /**
     * Once ever, a few seconds after joining the first world: how to open the settings.
     *
     * @param openKey the "open settings" key, or {@code null} when it could not be registered
     * @param chat    shows a message in chat
     */
    public static void tickWelcome(boolean inWorld, KeyMapping openKey, Consumer<Component> chat) {
        DistanceConfig config = AudioDistancePlugin.CONFIG;
        if (!inWorld) {
            ticksInWorld = 0;
            return;
        }
        if (config.isWelcomeShown() || ++ticksInWorld < WELCOME_DELAY_TICKS) {
            return;
        }
        config.setWelcomeShown(true);
        config.save();
        Component message = openKey != null && !openKey.isUnbound()
                ? Component.translatable("message.vc-audio-distance.welcome.key", openKey.getTranslatedKeyMessage())
                : Component.translatable("message.vc-audio-distance.welcome.button");
        chat.accept(message);
    }

    /** Shows the sound zone the server just put the player in (or that they left it). */
    public static void tickZoneNotice() {
        String zone = AudioDistancePlugin.LINK.consumeZoneNotice();
        if (zone != null) {
            HudOverlay.flash(zone.isEmpty()
                    ? Component.translatable("gui.vc-audio-distance.hud.zone_left")
                    : Component.translatable("gui.vc-audio-distance.hud.zone", zone));
        }
    }

    /** The "voice HUD" key: off, while talking, always. */
    public static void cycleHud() {
        DistanceConfig config = AudioDistancePlugin.CONFIG;
        config.setHudMode(config.getHudMode().next());
        config.save();
        HudOverlay.flash(HudOverlay.modeLabel(config.getHudMode()));
    }
}
