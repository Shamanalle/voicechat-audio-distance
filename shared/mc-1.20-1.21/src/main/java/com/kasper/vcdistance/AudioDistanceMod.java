package com.kasper.vcdistance;

import com.kasper.vcdistance.server.ServerThickness;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Common entrypoint (client and server): networking with the other side of the addon, and the
 * server tick that measures walls for server-side muffling and picks up edited server settings.
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
    }

    private void onServerTick(MinecraftServer server) {
        AudioDistancePlugin.SERVER_WALLS.tick(thickness);
        if (++ticks % RELOAD_CHECK_TICKS == 0 && AudioDistancePlugin.SERVER_SETTINGS.reloadIfChanged()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (AudioDistancePlugin.SERVER_WALLS.hasAddon(player.getUUID())) {
                    ModNetworking.sendProfile(player);
                }
            }
        }
    }
}
