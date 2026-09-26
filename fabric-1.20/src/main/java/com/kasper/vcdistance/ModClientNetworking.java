package com.kasper.vcdistance;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Client half of the link (Minecraft 1.20.1).
 */
public final class ModClientNetworking {

    private ModClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.PROFILE, (client, handler, buf, responseSender) -> {
            String text = buf.readUtf(LinkProtocol.MAX_LENGTH);
            AudioDistancePlugin.LINK.onProfile(text);
        });
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.NEARBY, (client, handler, buf, responseSender) -> {
            String text = buf.readUtf(LinkProtocol.MAX_LENGTH);
            AudioDistancePlugin.LINK.onNearby(text);
        });
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.ADMIN_REPLY, (client, handler, buf, responseSender) -> {
            String text = buf.readUtf(LinkProtocol.MAX_LENGTH);
            AudioDistancePlugin.LINK.onAdminReply(text);
        });
        AudioDistancePlugin.LINK.setAdminSender(text -> {
            if (ClientPlayNetworking.canSend(ModNetworking.ADMIN)) {
                FriendlyByteBuf buf = PacketByteBufs.create();
                buf.writeUtf(text, LinkProtocol.MAX_LENGTH);
                ClientPlayNetworking.send(ModNetworking.ADMIN, buf);
            }
        });
    }

    /** @return {@code true} once the hello was sent (the server has the addon) */
    public static boolean trySendHello(String modVersion) {
        if (!ClientPlayNetworking.canSend(ModNetworking.HELLO)) {
            return false;
        }
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(LinkProtocol.hello(modVersion), LinkProtocol.MAX_LENGTH);
        ClientPlayNetworking.send(ModNetworking.HELLO, buf);
        return true;
    }
}
