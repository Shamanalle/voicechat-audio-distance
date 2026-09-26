package com.kasper.vcdistance;

import com.kasper.vcdistance.server.AdminPermission;
import com.kasper.vcdistance.server.ServerBridge;
import com.kasper.vcdistance.server.ServerThickness;
import com.kasper.vcdistance.server.VcdCommand;
import com.kasper.vcdistance.server.ServerZones;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Common entrypoint (client and server): networking with the other side of the addon, and the
 * server tick that measures walls for server-side muffling, sends players with the addon the voice
 * chat state of those nearby, and picks up edited server settings.
 */
public class AudioDistanceMod implements ModInitializer {

    private static final int RELOAD_CHECK_TICKS = 40;

    private final ServerThickness thickness = new ServerThickness();
    private int ticks;

    @Override
    public void onInitialize() {
        ModNetworking.registerCommon();
        ModNetworking.registerServer();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> AudioDistancePlugin.ensureServerSettings());
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                VcdCommand.register(dispatcher, AdminPermission::isAdmin, AudioDistanceMod::resendProfiles));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerHooks.joined(handler.player.getUUID()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ServerHooks.left(handler.player.getUUID()));
    }

    private void onServerTick(MinecraftServer server) {
        AudioDistancePlugin.SERVER_WALLS.tick(thickness);
        // Players for the voice rules, and the addon requirement
        ServerBridge.tick(server);
        ++ticks;
        if (ticks % AudioDistancePlugin.NEARBY_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (AudioDistancePlugin.SERVER_WALLS.hasAddon(player.getUUID())) {
                    ModNetworking.sendNearby(player);
                    // Went to another dimension with its own profile
                    Zone zone = ServerZones.of(player);
                    if (AudioDistancePlugin.ZONES.changed(player.getUUID(), zone)) {
                        ModNetworking.sendProfile(player, zone);
                    }
                }
            }
        }
        if (ticks % RELOAD_CHECK_TICKS == 0 && AudioDistancePlugin.SERVER_SETTINGS.reloadIfChanged()) {
            resendProfiles(server);
        }
    }

    /** Every player with the addon gets their zone's profile again (after the settings changed). */
    static void resendProfiles(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (AudioDistancePlugin.SERVER_WALLS.hasAddon(player.getUUID())) {
                Zone zone = ServerZones.of(player);
                AudioDistancePlugin.ZONES.set(player.getUUID(), zone);
                ModNetworking.sendProfile(player, zone);
            }
        }
    }
}
