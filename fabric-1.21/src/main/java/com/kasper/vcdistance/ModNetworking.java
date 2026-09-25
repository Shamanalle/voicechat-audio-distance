package com.kasper.vcdistance;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Link between the client and server halves of the addon (Minecraft 1.21.x, payload API).
 * Both halves are optional: without the other side nothing is sent.
 */
public final class ModNetworking {

    /** Client to server: "I have the addon". */
    public record Hello(String text) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LinkProtocol.NAMESPACE, LinkProtocol.HELLO));
        public static final StreamCodec<ByteBuf, Hello> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_LENGTH).map(Hello::new, Hello::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: the server's sound profile. */
    public record Profile(String text) implements CustomPacketPayload {
        public static final Type<Profile> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LinkProtocol.NAMESPACE, LinkProtocol.PROFILE));
        public static final StreamCodec<ByteBuf, Profile> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_LENGTH).map(Profile::new, Profile::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: voice chat state of the players nearby. */
    public record Nearby(String text) implements CustomPacketPayload {
        public static final Type<Nearby> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LinkProtocol.NAMESPACE, LinkProtocol.NEARBY));
        public static final StreamCodec<ByteBuf, Nearby> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_LENGTH).map(Nearby::new, Nearby::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private ModNetworking() {
    }

    /** Registers the payload types; runs on both sides. */
    public static void registerCommon() {
        PayloadTypeRegistry.playC2S().register(Hello.TYPE, Hello.CODEC);
        PayloadTypeRegistry.playS2C().register(Profile.TYPE, Profile.CODEC);
        PayloadTypeRegistry.playS2C().register(Nearby.TYPE, Nearby.CODEC);
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(Hello.TYPE, (payload, context) -> onHello(context.player(), payload.text()));
    }

    private static void onHello(ServerPlayer player, String text) {
        if (LinkProtocol.parseHello(text) < 1) {
            return;
        }
        AudioDistancePlugin.SERVER_WALLS.markAddonListener(player.getUUID());
        sendProfile(player);
    }

    public static void sendProfile(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, Profile.TYPE)) {
            ServerPlayNetworking.send(player, new Profile(AudioDistancePlugin.serverProfileMessage()));
        }
    }

    /** Sends the voice chat state of the players nearby; skipped for clients without a 1.3+ addon. */
    public static void sendNearby(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, Nearby.TYPE)) {
            String text = AudioDistancePlugin.nearbyMessage(player, ModNetworking::visible);
            if (text != null) {
                ServerPlayNetworking.send(player, new Nearby(text));
            }
        }
    }

    /** Spectators are listed only to other spectators. */
    private static boolean visible(Object viewer, Object other) {
        return !((ServerPlayer) other).isSpectator() || ((ServerPlayer) viewer).isSpectator();
    }
}
