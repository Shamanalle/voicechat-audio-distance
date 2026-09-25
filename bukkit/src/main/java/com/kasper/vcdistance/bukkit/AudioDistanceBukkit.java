package com.kasper.vcdistance.bukkit;

import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.ModEnvironment;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Server side of the addon for Bukkit, Spigot, Paper and Purpur. Does the same as the Fabric server
 * side: muffles voices through walls for players without the addon and sends players who have it
 * the server's sound profile and the voice chat state of the players near them. The client half works with this plugin exactly as with a Fabric
 * server, because both speak the same {@link LinkProtocol} over the same channels.
 */
public final class AudioDistanceBukkit extends JavaPlugin implements Listener {

    static final String HELLO_CHANNEL = LinkProtocol.NAMESPACE + ":" + LinkProtocol.HELLO;
    static final String PROFILE_CHANNEL = LinkProtocol.NAMESPACE + ":" + LinkProtocol.PROFILE;
    static final String NEARBY_CHANNEL = LinkProtocol.NAMESPACE + ":" + LinkProtocol.NEARBY;

    private static final int RELOAD_CHECK_TICKS = 40;

    private final BukkitThickness thickness = new BukkitThickness();
    private int ticks;

    @Override
    public void onLoad() {
        // Settings live in plugins/<name>/ instead of the loader's config directory
        ModEnvironment.setConfigDir(getDataFolder().toPath());
    }

    @Override
    public void onEnable() {
        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            getLogger().severe("Simple Voice Chat was not found, disabling");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        AudioDistancePlugin.ensureServerSettings();
        service.registerPlugin(new ServerPlugin());

        getServer().getMessenger().registerOutgoingPluginChannel(this, PROFILE_CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, NEARBY_CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, HELLO_CHANNEL,
                (channel, player, message) -> onHello(player, message));
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        AudioDistancePlugin.SERVER_WALLS.clear();
        BlockAcoustics.clearCache();
    }

    /** Main thread, every tick: measures walls, sends nearby voice states and picks up edited settings. */
    private void tick() {
        AudioDistancePlugin.SERVER_WALLS.tick(thickness);
        ++ticks;
        if (ticks % AudioDistancePlugin.NEARBY_INTERVAL_TICKS == 0) {
            for (Player player : getServer().getOnlinePlayers()) {
                if (AudioDistancePlugin.SERVER_WALLS.hasAddon(player.getUniqueId())
                        && player.getListeningPluginChannels().contains(NEARBY_CHANNEL)) {
                    sendNearby(player);
                }
            }
        }
        if (ticks % RELOAD_CHECK_TICKS == 0 && AudioDistancePlugin.SERVER_SETTINGS.reloadIfChanged()) {
            BlockAcoustics.clearCache();
            for (Player player : getServer().getOnlinePlayers()) {
                if (AudioDistancePlugin.SERVER_WALLS.hasAddon(player.getUniqueId())) {
                    sendProfile(player);
                }
            }
        }
    }

    private void onHello(Player player, byte[] message) {
        if (LinkProtocol.parseHello(LinkProtocol.decode(message)) < 1) {
            return;
        }
        AudioDistancePlugin.SERVER_WALLS.markAddonListener(player.getUniqueId());
        sendProfile(player);
    }

    private void sendProfile(Player player) {
        // Silently skipped by Bukkit when the client did not register the channel
        player.sendPluginMessage(this, PROFILE_CHANNEL, LinkProtocol.encode(AudioDistancePlugin.serverProfileMessage()));
    }

    private void sendNearby(Player player) {
        String text = AudioDistancePlugin.nearbyMessage(player, AudioDistanceBukkit::visible);
        if (text != null) {
            player.sendPluginMessage(this, NEARBY_CHANNEL, LinkProtocol.encode(text));
        }
    }

    /** Players hidden from the viewer (vanish plugins) are never listed; spectators only to spectators. */
    private static boolean visible(Object viewer, Object other) {
        Player v = (Player) viewer;
        Player o = (Player) other;
        return v.canSee(o) && (o.getGameMode() != GameMode.SPECTATOR || v.getGameMode() == GameMode.SPECTATOR);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        AudioDistancePlugin.SERVER_WALLS.forgetPlayer(event.getPlayer().getUniqueId());
    }

    /** Simple Voice Chat plugin with only the server-side events (there is no client on Bukkit). */
    private static final class ServerPlugin implements VoicechatPlugin {

        @Override
        public String getPluginId() {
            return AudioDistancePlugin.MOD_ID;
        }

        @Override
        public void initialize(VoicechatApi api) {
            new AudioDistancePlugin().initialize(api);
        }

        @Override
        public void registerEvents(EventRegistration registration) {
            AudioDistancePlugin.registerServerEvents(registration);
        }
    }
}
