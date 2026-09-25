package com.kasper.vcdistance.neoforge;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.Zone;
import com.kasper.vcdistance.server.ServerZones;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The client-server link on NeoForge: the same three messages and channels as on Fabric and Paper,
 * so a NeoForge client works with any of those servers and the other way round. Registered as
 * optional, so either side may be missing.
 */
public final class NeoNetworking {

    public record Hello(String text) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LinkProtocol.NAMESPACE, LinkProtocol.HELLO));
        public static final StreamCodec<ByteBuf, Hello> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_LENGTH).map(Hello::new, Hello::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Profile(String text) implements CustomPacketPayload {
        public static final Type<Profile> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LinkProtocol.NAMESPACE, LinkProtocol.PROFILE));
        public static final StreamCodec<ByteBuf, Profile> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_LENGTH).map(Profile::new, Profile::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Nearby(String text) implements CustomPacketPayload {
        public static final Type<Nearby> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LinkProtocol.NAMESPACE, LinkProtocol.NEARBY));
        public static final StreamCodec<ByteBuf, Nearby> CODEC = ByteBufCodecs.stringUtf8(LinkProtocol.MAX_LENGTH).map(Nearby::new, Nearby::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private NeoNetworking() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(Hello.TYPE, Hello.CODEC, (payload, context) ->
                context.enqueueWork(() -> onHello((ServerPlayer) context.player(), payload.text())));
        registrar.playToClient(Profile.TYPE, Profile.CODEC, (payload, context) ->
                AudioDistancePlugin.LINK.onProfile(payload.text()));
        registrar.playToClient(Nearby.TYPE, Nearby.CODEC, (payload, context) ->
                AudioDistancePlugin.LINK.onNearby(payload.text()));
    }

    private static void onHello(ServerPlayer player, String text) {
        if (LinkProtocol.parseHello(text) < 1) {
            return;
        }
        AudioDistancePlugin.SERVER_WALLS.markAddonListener(player.getUUID());
        Zone zone = ServerZones.of(player);
        AudioDistancePlugin.ZONES.set(player.getUUID(), zone);
        sendProfile(player, zone);
    }

    static void sendProfile(ServerPlayer player, Zone zone) {
        if (player.connection.hasChannel(Profile.TYPE)) {
            PacketDistributor.sendToPlayer(player, new Profile(AudioDistancePlugin.serverProfileMessage(zone)));
        }
    }

    static void sendNearby(ServerPlayer player) {
        if (player.connection.hasChannel(Nearby.TYPE)) {
            String text = AudioDistancePlugin.nearbyMessage(player, NeoNetworking::visible);
            if (text != null) {
                PacketDistributor.sendToPlayer(player, new Nearby(text));
            }
        }
    }

    /** Spectators are listed only to other spectators. */
    private static boolean visible(Object viewer, Object other) {
        return !((ServerPlayer) other).isSpectator() || ((ServerPlayer) viewer).isSpectator();
    }
}
