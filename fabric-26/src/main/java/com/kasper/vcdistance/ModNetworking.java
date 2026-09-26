package com.kasper.vcdistance;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import com.kasper.vcdistance.server.ServerBridge;
import com.kasper.vcdistance.server.ServerZones;
import net.minecraft.server.level.ServerPlayer;

/**
 * Link between the client and server halves of the addon (Minecraft 26.x, payload API).
 * Both halves are optional: without the other side nothing is sent.
 */
public final class ModNetworking {

    /** Client to server: "I have the addon". */
    public record Hello(String text) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LinkProtocol.NAMESPACE, LinkProtocol.HELLO));
        public static final StreamCodec<ByteBuf, Hello> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_LENGTH).map(Hello::new, Hello::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: the server's sound profile. */
    public record Profile(String text) implements CustomPacketPayload {
        public static final Type<Profile> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LinkProtocol.NAMESPACE, LinkProtocol.PROFILE));
        public static final StreamCodec<ByteBuf, Profile> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_LENGTH).map(Profile::new, Profile::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: voice chat state of the players nearby. */
    public record Nearby(String text) implements CustomPacketPayload {
        public static final Type<Nearby> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LinkProtocol.NAMESPACE, LinkProtocol.NEARBY));
        public static final StreamCodec<ByteBuf, Nearby> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_LENGTH).map(Nearby::new, Nearby::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: a command from the Server tab. */
    public record Admin(String text) implements CustomPacketPayload {
        public static final Type<Admin> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LinkProtocol.NAMESPACE, LinkProtocol.ADMIN));
        public static final StreamCodec<ByteBuf, Admin> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_LENGTH).map(Admin::new, Admin::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: the reply and the server's settings, for the Server tab. */
    public record AdminReply(String text) implements CustomPacketPayload {
        public static final Type<AdminReply> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LinkProtocol.NAMESPACE, LinkProtocol.ADMIN_REPLY));
        public static final StreamCodec<ByteBuf, AdminReply> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_LENGTH).map(AdminReply::new, AdminReply::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private ModNetworking() {
    }

    /** Registers the payload types; runs on both sides. */
    public static void registerCommon() {
        PayloadTypeRegistry.serverboundPlay().register(Hello.TYPE, Hello.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Profile.TYPE, Profile.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Nearby.TYPE, Nearby.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Admin.TYPE, Admin.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AdminReply.TYPE, AdminReply.CODEC);
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(Hello.TYPE, (payload, context) -> onHello(context.player(), payload.text()));
        ServerPlayNetworking.registerGlobalReceiver(Admin.TYPE, (payload, context) -> onAdmin(context.player(), payload.text()));
    }

    private static void onHello(ServerPlayer player, String text) {
        if (!ServerHooks.hello(player.getUUID(), text)) {
            return;
        }
        Zone zone = ServerZones.of(player);
        AudioDistancePlugin.ZONES.set(player.getUUID(), zone);
        sendProfile(player, zone);
    }

    /** Sends the profile as it applies in {@code zone} ({@code null}: the main profile). */
    public static void sendProfile(ServerPlayer player, Zone zone) {
        if (ServerPlayNetworking.canSend(player, Profile.TYPE)) {
            ServerPlayNetworking.send(player, new Profile(AudioDistancePlugin.serverProfileMessage(zone, ServerBridge.isAdmin(player))));
        }
    }

    /** A command from the player's Server tab; answered only for admins. */
    private static void onAdmin(ServerPlayer player, String text) {
        String reply = ServerBridge.admin(player, text, AudioDistanceMod::resendProfiles);
        if (reply != null && ServerPlayNetworking.canSend(player, AdminReply.TYPE)) {
            ServerPlayNetworking.send(player, new AdminReply(reply));
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
