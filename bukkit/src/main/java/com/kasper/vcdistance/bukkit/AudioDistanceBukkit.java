package com.kasper.vcdistance.bukkit;

import com.kasper.vcdistance.AdminCommands;
import com.kasper.vcdistance.AudioDistancePlugin;
import com.kasper.vcdistance.LinkProtocol;
import com.kasper.vcdistance.ModEnvironment;
import com.kasper.vcdistance.ServerHooks;
import com.kasper.vcdistance.ServerPlayers;
import com.kasper.vcdistance.Zone;
import com.kasper.vcdistance.ZoneTracker;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    static final String ADMIN_CHANNEL = LinkProtocol.NAMESPACE + ":" + LinkProtocol.ADMIN;
    static final String ADMIN_REPLY_CHANNEL = LinkProtocol.NAMESPACE + ":" + LinkProtocol.ADMIN_REPLY;
    static final String ADMIN_PERMISSION = "vcd.admin";

    private static final int RELOAD_CHECK_TICKS = 40;

    private final BukkitThickness thickness = new BukkitThickness();
    private final ZoneTracker zones = new ZoneTracker();
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
        getServer().getMessenger().registerOutgoingPluginChannel(this, ADMIN_REPLY_CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, HELLO_CHANNEL,
                (channel, player, message) -> onHello(player, message));
        getServer().getMessenger().registerIncomingPluginChannel(this, ADMIN_CHANNEL,
                (channel, player, message) -> onAdmin(player, message));
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        PluginCommand command = getCommand(AdminCommands.NAME);
        if (command != null) {
            AdminCommand handler = new AdminCommand();
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        AudioDistancePlugin.SERVER_WALLS.clear();
        BlockAcoustics.clearCache();
        zones.clear();
    }

    /** Main thread, every tick: measures walls, sends nearby voice states and picks up edited settings. */
    private void tick() {
        AudioDistancePlugin.SERVER_WALLS.tick(thickness);
        refreshPlayers();
        ++ticks;
        if (ticks % AudioDistancePlugin.NEARBY_INTERVAL_TICKS == 0) {
            for (Player player : getServer().getOnlinePlayers()) {
                if (!AudioDistancePlugin.SERVER_WALLS.hasAddon(player.getUniqueId())) {
                    continue;
                }
                if (player.getListeningPluginChannels().contains(NEARBY_CHANNEL)) {
                    sendNearby(player);
                }
                // Walked into another world or region with its own profile
                Zone zone = zoneOf(player);
                if (zones.changed(player.getUniqueId(), zone)) {
                    sendProfile(player, zone);
                }
            }
        }
        if (ticks % RELOAD_CHECK_TICKS == 0 && AudioDistancePlugin.SERVER_SETTINGS.reloadIfChanged()) {
            BlockAcoustics.clearCache();
            resendProfiles();
        }
    }

    /** Every few ticks: the players for the voice rules, and the addon requirement. */
    private void refreshPlayers() {
        if (!ServerHooks.refreshDue()) {
            return;
        }
        List<ServerPlayers.Info> online = new ArrayList<>();
        for (Player p : getServer().getOnlinePlayers()) {
            try {
                online.add(info(p));
            } catch (Throwable ignored) {
                // A player half-way through joining or leaving
            }
        }
        ServerHooks.refresh(online, new ServerHooks.Platform() {
            @Override
            public void message(UUID player, String text) {
                Player p = getServer().getPlayer(player);
                if (p != null) {
                    p.sendMessage(text);
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void kick(UUID player, String text) {
                Player p = getServer().getPlayer(player);
                if (p != null) {
                    p.kickPlayer(text);
                }
            }
        });
    }

    /** A player as the voice rules see them. */
    @SuppressWarnings("deprecation")
    static ServerPlayers.Info info(Player p) {
        Location at = p.getLocation();
        boolean regions = AudioDistancePlugin.SERVER_SETTINGS.zones().keySet().stream().anyMatch(k -> k.startsWith(Zone.REGION + ":"));
        String language = "";
        try {
            language = p.getLocale();
        } catch (Throwable ignored) {
        }
        return new ServerPlayers.Info(p.getUniqueId(), p.getName(), p.getWorld().getName(),
                at.getX(), at.getY(), at.getZ(),
                p.isSneaking(), !p.isDead(), p.getGameMode() == GameMode.SPECTATOR,
                item(p.getInventory().getItemInMainHand()), item(p.getInventory().getItemInOffHand()),
                regions ? WorldGuardRegions.at(at) : List.of(), language == null ? "" : language);
    }

    private static String item(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "";
        }
        try {
            return stack.getType().getKey().toString();
        } catch (Throwable t) {
            return "minecraft:" + stack.getType().name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    static boolean isAdmin(Player player) {
        return player.isOp() || player.hasPermission(ADMIN_PERMISSION);
    }

    /** A command from the player's Server tab; answered only for admins. */
    private void onAdmin(Player player, byte[] message) {
        AudioDistancePlugin.PLAYERS.update(info(player));
        String reply = ServerHooks.admin(player.getUniqueId(), LinkProtocol.decode(message), isAdmin(player),
                context(player.getUniqueId()));
        if (reply != null) {
            player.sendPluginMessage(this, ADMIN_REPLY_CHANNEL, LinkProtocol.encode(reply));
        }
    }

    private void onHello(Player player, byte[] message) {
        if (!ServerHooks.hello(player.getUniqueId(), LinkProtocol.decode(message))) {
            return;
        }
        Zone zone = zoneOf(player);
        zones.set(player.getUniqueId(), zone);
        sendProfile(player, zone);
    }

    private void sendProfile(Player player, Zone zone) {
        // Silently skipped by Bukkit when the client did not register the channel
        player.sendPluginMessage(this, PROFILE_CHANNEL, LinkProtocol.encode(AudioDistancePlugin.serverProfileMessage(zone, isAdmin(player))));
    }

    /** Every player with the addon gets their zone's profile again (after the settings changed). */
    private void resendProfiles() {
        for (Player player : getServer().getOnlinePlayers()) {
            if (AudioDistancePlugin.SERVER_WALLS.hasAddon(player.getUniqueId())) {
                Zone zone = zoneOf(player);
                zones.set(player.getUniqueId(), zone);
                sendProfile(player, zone);
            }
        }
    }

    private static Zone zoneOf(Player player) {
        if (AudioDistancePlugin.SERVER_SETTINGS.zones().isEmpty()) {
            return null;
        }
        return AudioDistancePlugin.SERVER_SETTINGS.zoneOf(info(player));
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
    public void onJoin(PlayerJoinEvent event) {
        ServerHooks.joined(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ServerHooks.left(event.getPlayer().getUniqueId());
        zones.forget(event.getPlayer().getUniqueId());
    }

    /** What /vcd needs from the server, for a command run by {@code sender} ({@code null}: the console). */
    private AdminCommands.Context context(UUID sender) {
        return new AdminCommands.Context() {
            @Override
            public String platform() {
                return getServer().getName();
            }

            @Override
            public int onlinePlayers() {
                return getServer().getOnlinePlayers().size();
            }

            @Override
            public int addonPlayers() {
                int n = 0;
                for (Player p : getServer().getOnlinePlayers()) {
                    if (AudioDistancePlugin.SERVER_WALLS.hasAddon(p.getUniqueId())) {
                        n++;
                    }
                }
                return n;
            }

            @Override
            public void resendProfiles() {
                AudioDistanceBukkit.this.resendProfiles();
            }

            @Override
            public void afterSettingsChange() {
                BlockAcoustics.clearCache();
            }

            @Override
            public UUID sender() {
                return sender;
            }
        };
    }

    /** {@code /vcd}: for operators and players with the vcd.admin permission (see plugin.yml). */
    private final class AdminCommand implements TabExecutor {

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            UUID id = null;
            if (sender instanceof Player p) {
                // Commands like zone pos1 need where the admin stands right now
                AudioDistancePlugin.PLAYERS.update(info(p));
                id = p.getUniqueId();
            }
            for (String line : AdminCommands.run(String.join(" ", args), AudioDistancePlugin.SERVER_SETTINGS, context(id))) {
                sender.sendMessage(line);
            }
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            return AdminCommands.suggest(String.join(" ", args));
        }
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
