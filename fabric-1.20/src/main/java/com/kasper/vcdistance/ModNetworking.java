package com.kasper.vcdistance;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Link between the client and server halves of the addon (Minecraft 1.20.1, channel-based API).
 * Both halves are optional: without the other side nothing is sent.
 */
public final class ModNetworking {

    public static final ResourceLocation HELLO = new ResourceLocation(LinkProtocol.NAMESPACE, LinkProtocol.HELLO);
    public static final ResourceLocation PROFILE = new ResourceLocation(LinkProtocol.NAMESPACE, LinkProtocol.PROFILE);

    private ModNetworking() {
    }

    /** Payload types (nothing to register with the 1.20.1 channel API). */
    public static void registerCommon() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(HELLO, (server, player, handler, buf, responseSender) -> {
            String text = buf.readUtf(LinkProtocol.MAX_LENGTH);
            server.execute(() -> onHello(player, text));
        });
    }

    private static void onHello(ServerPlayer player, String text) {
        if (LinkProtocol.parseHello(text) < 1) {
            return;
        }
        AudioDistancePlugin.SERVER_WALLS.markAddonListener(player.getUUID());
        sendProfile(player);
    }

    public static void sendProfile(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, PROFILE)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeUtf(AudioDistancePlugin.serverProfileMessage(), LinkProtocol.MAX_LENGTH);
            ServerPlayNetworking.send(player, PROFILE, buf);
        }
    }
}
