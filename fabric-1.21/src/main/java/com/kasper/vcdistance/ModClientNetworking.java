package com.kasper.vcdistance;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client half of the link (Minecraft 1.21.x).
 */
public final class ModClientNetworking {

    private ModClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.Profile.TYPE,
                (payload, context) -> AudioDistancePlugin.LINK.onProfile(payload.text()));
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.Nearby.TYPE,
                (payload, context) -> AudioDistancePlugin.LINK.onNearby(payload.text()));
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.AdminReply.TYPE,
                (payload, context) -> AudioDistancePlugin.LINK.onAdminReply(payload.text()));
        AudioDistancePlugin.LINK.setAdminSender(text -> {
            if (ClientPlayNetworking.canSend(ModNetworking.Admin.TYPE)) {
                ClientPlayNetworking.send(new ModNetworking.Admin(text));
            }
        });
    }

    /** @return {@code true} once the hello was sent (the server has the addon) */
    public static boolean trySendHello(String modVersion) {
        if (!ClientPlayNetworking.canSend(ModNetworking.Hello.TYPE)) {
            return false;
        }
        ClientPlayNetworking.send(new ModNetworking.Hello(LinkProtocol.hello(modVersion)));
        return true;
    }
}
