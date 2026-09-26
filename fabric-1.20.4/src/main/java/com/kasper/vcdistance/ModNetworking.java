package com.kasper.vcdistance;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import com.kasper.vcdistance.server.ServerBridge;
import com.kasper.vcdistance.server.ServerZones;
import net.minecraft.server.level.ServerPlayer;

/**
 * Link between the client and server halves of the addon (Minecraft 1.20.2 - 1.20.4, channel-based API).
 * Both halves are optional: without the other side nothing is sent.
 */
public final class ModNetworking {

    public static final ResourceLocation HELLO = new ResourceLocation(LinkProtocol.NAMESPACE, LinkProtocol.HELLO);
    public static final ResourceLocation PROFILE = new ResourceLocation(LinkProtocol.NAMESPACE, LinkProtocol.PROFILE);
    public static final ResourceLocation NEARBY = new ResourceLocation(LinkProtocol.NAMESPACE, LinkProtocol.NEARBY);
    public static final ResourceLocation ADMIN = new ResourceLocation(LinkProtocol.NAMESPACE, LinkProtocol.ADMIN);
    public static final ResourceLocation ADMIN_REPLY = new ResourceLocation(LinkProtocol.NAMESPACE, LinkProtocol.ADMIN_REPLY);

    private ModNetworking() {
    }

    /** Payload types (nothing to register with the channel API). */
    public static void registerCommon() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(HELLO, (server, player, handler, buf, responseSender) -> {
            String text = buf.readUtf(LinkProtocol.MAX_LENGTH);
            server.execute(() -> onHello(player, text));
        });
        ServerPlayNetworking.registerGlobalReceiver(ADMIN, (server, player, handler, buf, responseSender) -> {
            String text = buf.readUtf(LinkProtocol.MAX_LENGTH);
            server.execute(() -> onAdmin(player, text));
        });
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
        if (ServerPlayNetworking.canSend(player, PROFILE)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeUtf(AudioDistancePlugin.serverProfileMessage(zone, ServerBridge.isAdmin(player)), LinkProtocol.MAX_LENGTH);
            ServerPlayNetworking.send(player, PROFILE, buf);
        }
    }

    /** A command from the player's Server tab; answered only for admins. */
    private static void onAdmin(ServerPlayer player, String text) {
        String reply = ServerBridge.admin(player, text, AudioDistanceMod::resendProfiles);
        if (reply != null && ServerPlayNetworking.canSend(player, ADMIN_REPLY)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeUtf(reply, LinkProtocol.MAX_LENGTH);
            ServerPlayNetworking.send(player, ADMIN_REPLY, buf);
        }
    }

    /** Sends the voice chat state of the players nearby; skipped for clients without a 1.3+ addon. */
    public static void sendNearby(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, NEARBY)) {
            String text = AudioDistancePlugin.nearbyMessage(player, ModNetworking::visible);
            if (text != null) {
                FriendlyByteBuf buf = PacketByteBufs.create();
                buf.writeUtf(text, LinkProtocol.MAX_LENGTH);
                ServerPlayNetworking.send(player, NEARBY, buf);
            }
        }
    }

    /** Spectators are listed only to other spectators. */
    private static boolean visible(Object viewer, Object other) {
        return !((ServerPlayer) other).isSpectator() || ((ServerPlayer) viewer).isSpectator();
    }
}
