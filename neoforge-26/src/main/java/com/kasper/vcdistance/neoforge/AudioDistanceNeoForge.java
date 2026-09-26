package com.kasper.vcdistance.neoforge;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.ServerHooks;
import com.kasper.vcdistance.Zone;
import com.kasper.vcdistance.server.AdminPermission;
import com.kasper.vcdistance.server.ServerBridge;
import com.kasper.vcdistance.server.ServerThickness;
import com.kasper.vcdistance.server.ServerZones;
import com.kasper.vcdistance.server.VcdCommand;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge entrypoint (Minecraft 26.x): the full addon, as on Fabric. Minecraft 26.x is not
 * obfuscated, so the client and server code shared with the Fabric build runs here unchanged;
 * only the hooks into the loader live in this package. The Simple Voice Chat plugin itself is found
 * through {@code @ForgeVoicechatPlugin}.
 */
@Mod(AudioDistanceNeoForge.MOD_ID)
public final class AudioDistanceNeoForge {

    public static final String MOD_ID = "vc_audio_distance";
    private static final int RELOAD_CHECK_TICKS = 40;

    private final ServerThickness thickness = new ServerThickness();
    private int ticks;

    public AudioDistanceNeoForge(IEventBus modBus, ModContainer container, Dist dist) {
        modBus.addListener(NeoNetworking::register);
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> AudioDistancePlugin.ensureServerSettings());
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) ->
                VcdCommand.register(e.getDispatcher(), AdminPermission::isAdmin, AudioDistanceNeoForge::resendProfiles));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> ServerHooks.joined(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> ServerHooks.left(e.getEntity().getUUID()));
        if (dist.isClient()) {
            NeoClient.init(modBus, container);
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        AudioDistancePlugin.SERVER_WALLS.tick(thickness);
        ServerBridge.tick(server);
        ++ticks;
        if (ticks % AudioDistancePlugin.NEARBY_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (AudioDistancePlugin.SERVER_WALLS.hasAddon(player.getUUID())) {
                    NeoNetworking.sendNearby(player);
                    Zone zone = ServerZones.of(player);
                    if (AudioDistancePlugin.ZONES.changed(player.getUUID(), zone)) {
                        NeoNetworking.sendProfile(player, zone);
                    }
                }
            }
        }
        if (ticks % RELOAD_CHECK_TICKS == 0 && AudioDistancePlugin.SERVER_SETTINGS.reloadIfChanged()) {
            resendProfiles(server);
        }
    }

    static void resendProfiles(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (AudioDistancePlugin.SERVER_WALLS.hasAddon(player.getUUID())) {
                Zone zone = ServerZones.of(player);
                AudioDistancePlugin.ZONES.set(player.getUUID(), zone);
                NeoNetworking.sendProfile(player, zone);
            }
        }
    }
}
