package com.kasper.vcdistance;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.ClientEvent;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import org.lwjgl.openal.AL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;

/**
 * Simple Voice Chat plugin, on both sides.
 * <p>
 * Client: shapes the OpenAL distance curve of every positional voice and muffles voices heard
 * through walls. Server: muffles voices through walls for players who do not have the addon
 * ({@link ServerWalls}).
 * <p>
 * This class only depends on the SVC API and LWJGL, so the same code runs on every Minecraft
 * version and loader. The Bukkit plugin registers only {@link #registerServerEvents}. World access (raycasts, entity lookups) lives in the glue of each version,
 * which feeds {@link #SPEAKERS} and {@link #SERVER_WALLS} from the game threads.
 */
@ForgeVoicechatPlugin
public class AudioDistancePlugin implements VoicechatPlugin {

    public static final String MOD_ID = "vc-audio-distance";
    public static final double FALLBACK_DISTANCE = 48.0;

    /** The player's own settings (client). */
    public static final DistanceConfig CONFIG = new DistanceConfig();
    public static final SpeakerRegistry SPEAKERS = new SpeakerRegistry();
    /** What the client knows about the server it is connected to. */
    public static final ServerLink LINK = new ServerLink();
    /** Players within voice range of the listener (client). */
    public static final NearbyPlayers NEARBY = new NearbyPlayers();
    /** Echo, water and weather around the listener (client). */
    public static final ListenerEnvironment ENVIRONMENT = new ListenerEnvironment();
    /** Server-side settings and wall muffling. */
    public static final ServerSettings SERVER_SETTINGS = new ServerSettings();
    public static final ServerWalls SERVER_WALLS = new ServerWalls(SERVER_SETTINGS);

    private static volatile VoicechatApi api;
    private static volatile VoicechatServerApi serverApi;
    private static volatile VoicechatClientApi clientApi;
    /** Set when the installed Simple Voice Chat is too old to report other players' state (before 2.6.1). */
    private static volatile boolean clientStatesUnsupported;
    private static volatile long selfTalkNanos = Long.MIN_VALUE;
    private static volatile boolean selfWhispering;
    /** How long after the last microphone frame you still count as talking. */
    private static final long SELF_TALK_HOLD_NANOS = TimeUnit.MILLISECONDS.toNanos(400);
    private static volatile boolean serverSettingsLoaded;
    private static volatile boolean occlusionProviderAvailable;

    public enum OcclusionStatus {
        /** Walls are traced and voices are muffled. */
        ACTIVE,
        /** Turned off by the user. */
        OFF,
        /** Sound Physics Remastered handles voice occlusion instead. */
        SOUND_PHYSICS,
        /** This build has no world access (Forge/NeoForge lite builds). */
        UNAVAILABLE
    }

    @Override
    public String getPluginId() {
        return MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi voicechatApi) {
        api = voicechatApi;
        if (voicechatApi instanceof VoicechatServerApi server) {
            serverApi = server;
        }
        // The client config is loaded by the client entrypoint or on the first voice frame,
        // so dedicated servers never create it.
        DistanceConfig.LOGGER.info("VoiceChat Audio Distance plugin initialized");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        // The base event fires after SVC has positioned the source and set its own linear curve.
        registration.registerEvent(OpenALSoundEvent.class, AudioDistancePlugin::onOpenALSound);
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, AudioDistancePlugin::onEntitySound);
        registration.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, AudioDistancePlugin::onLocationalSound);
        registration.registerEvent(ClientSoundEvent.class, AudioDistancePlugin::onOwnVoice);
        registration.registerEvent(ClientVoicechatConnectionEvent.class, AudioDistancePlugin::captureClientApi);
        registerServerEvents(registration);
    }

    /** Server-side events: wall muffling for players without the addon. */
    public static void registerServerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, e -> {
            serverApi = e.getVoicechat();
            ensureServerSettings();
        });
        registration.registerEvent(VoicechatServerStoppedEvent.class, e -> {
            serverApi = null;
            SERVER_WALLS.clear();
        });
        registration.registerEvent(EntitySoundPacketEvent.class, SERVER_WALLS::onEntitySound);
        registration.registerEvent(LocationalSoundPacketEvent.class, SERVER_WALLS::onLocationalSound);
        // Only the voice connection closed; leaving the game is reported by the platform glue
        registration.registerEvent(PlayerDisconnectedEvent.class, e -> SERVER_WALLS.releaseListener(e.getPlayerUuid()));
    }

    // -------------------------------------------------------------------------
    // Public state
    // -------------------------------------------------------------------------

    /** The configuration in effect: the server's profile when it is enforced, otherwise the player's own. */
    public static DistanceConfig config() {
        return LINK.effective(CONFIG);
    }

    /** Loads the server settings once (both the server entrypoint and SVC may trigger it first). */
    public static synchronized void ensureServerSettings() {
        if (!serverSettingsLoaded) {
            serverSettingsLoaded = true;
            SERVER_SETTINGS.load();
        }
    }

    /** Text of the {@code profile} message for players who have the addon. */
    public static String serverProfileMessage() {
        double voice = FALLBACK_DISTANCE;
        double whisper = FALLBACK_DISTANCE / 2.0;
        VoicechatServerApi s = serverApi;
        if (s != null) {
            try {
                voice = s.getVoiceChatDistance();
                whisper = s.getServerConfig().getDouble("whisper_distance", voice / 2.0);
            } catch (Throwable ignored) {
            }
        }
        return LinkProtocol.profile(SERVER_SETTINGS, voice, whisper);
    }

    /** How often the server sends {@code nearby} messages, in ticks. */
    public static final int NEARBY_INTERVAL_TICKS = 20;

    /**
     * Text of the {@code nearby} message for one player with the addon: the voice chat state of the
     * players within voice range, closest first. Server main thread.
     *
     * @param player  the receiving player (the platform's player object)
     * @param visible whether the second player may be shown to the first (vanish, spectators)
     * @return the message, or {@code null} when Simple Voice Chat is not running
     */
    public static String nearbyMessage(Object player, BiPredicate<Object, Object> visible) {
        VoicechatServerApi s = serverApi;
        if (s == null || player == null) {
            return null;
        }
        try {
            ServerPlayer self = s.fromServerPlayer(player);
            UUID selfId = self.getUuid();
            double range = s.getVoiceChatDistance();
            List<ServerPlayer> near = new ArrayList<>(s.getPlayersInRange(self.getServerLevel(), self.getPosition(), range,
                    p -> !p.getUuid().equals(selfId) && visible.test(player, p.getPlayer())));
            near.sort(Comparator.comparingDouble(p -> squaredDistance(self, p)));
            Map<UUID, VoiceState> states = new LinkedHashMap<>();
            for (ServerPlayer p : near) {
                VoicechatConnection c = s.getConnectionOf(p.getUuid());
                states.put(p.getUuid(), c == null ? VoiceState.NO_VOICE_CHAT
                        : VoiceState.of(c.isInstalled(), c.isConnected(), c.isDisabled(), c.isInGroup()));
            }
            return LinkProtocol.nearby(states);
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Could not list nearby players: {}", t.toString());
            return null;
        }
    }

    private static double squaredDistance(ServerPlayer a, ServerPlayer b) {
        Position pa = a.getPosition();
        Position pb = b.getPosition();
        double dx = pa.getX() - pb.getX();
        double dy = pa.getY() - pb.getY();
        double dz = pa.getZ() - pb.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    // -------------------------------------------------------------------------
    // Your own voice and other players' voice chat state (client)
    // -------------------------------------------------------------------------

    private static void captureClientApi(ClientEvent event) {
        if (clientApi == null) {
            try {
                clientApi = event.getVoicechat();
            } catch (Throwable ignored) {
            }
        }
    }

    /** Microphone frame being sent: you are talking. Never changes the audio. */
    private static void onOwnVoice(ClientSoundEvent event) {
        captureClientApi(event);
        selfWhispering = event.isWhispering();
        selfTalkNanos = System.nanoTime();
    }

    /** {@code true} while your microphone is sending. */
    public static boolean isSelfTalking(long nowNanos) {
        long t = selfTalkNanos;
        return t != Long.MIN_VALUE && nowNanos - t <= SELF_TALK_HOLD_NANOS;
    }

    /** Whether your last microphone frame was a whisper. */
    public static boolean isSelfWhispering() {
        return selfWhispering;
    }

    /**
     * Voice chat state of a player: from the server when it has the addon (it also knows who has no
     * voice chat and who is in a group), otherwise from your own Simple Voice Chat (2.6.1+), which
     * knows who is disconnected or has the sound off.
     *
     * @return the state, or {@code null} when neither knows
     */
    public static VoiceState voiceState(UUID player, long nowNanos) {
        VoiceState fromServer = LINK.voiceState(player, nowNanos);
        return fromServer != null ? fromServer : clientVoiceState(player);
    }

    /** {@code true} when {@link #voiceState} can answer for nearby players. */
    public static boolean hasVoiceStates(long nowNanos) {
        return LINK.hasVoiceStates(nowNanos) || clientStatesAvailable();
    }

    private static boolean clientStatesAvailable() {
        VoicechatClientApi c = clientApi;
        if (c == null || clientStatesUnsupported) {
            return false;
        }
        try {
            // Your own voice chat must be connected for the others' state to mean anything
            return !c.isDisconnected();
        } catch (Throwable t) {
            clientStatesUnsupported = true;
            return false;
        }
    }

    private static VoiceState clientVoiceState(UUID player) {
        if (player == null || !clientStatesAvailable()) {
            return null;
        }
        VoicechatClientApi c = clientApi;
        try {
            if (c.isDisconnected(player)) {
                return VoiceState.DISCONNECTED;
            }
            return c.isDisabled(player) ? VoiceState.SOUND_OFF : VoiceState.CONNECTED;
        } catch (Throwable t) {
            clientStatesUnsupported = true;
            return null;
        }
    }

    /** Called by the client glue once it feeds occlusion data from the world. */
    public static void markOcclusionProviderAvailable() {
        occlusionProviderAvailable = true;
    }

    public static OcclusionStatus occlusionStatus() {
        if (!occlusionProviderAvailable) {
            return OcclusionStatus.UNAVAILABLE;
        }
        if (ModEnvironment.isSoundPhysicsPresent()) {
            return OcclusionStatus.SOUND_PHYSICS;
        }
        return config().isOcclusionEnabled() ? OcclusionStatus.ACTIVE : OcclusionStatus.OFF;
    }

    /** Voice distance configured on the connected server, in blocks. */
    public static double getServerMaxDistance() {
        VoicechatApi a = api;
        if (a != null) {
            try {
                double d = a.getVoiceChatDistance();
                if (d > 0.0) {
                    return d;
                }
            } catch (Throwable ignored) {
            }
        }
        return FALLBACK_DISTANCE;
    }

    /**
     * Gain the current curve gives at {@code distance} blocks, for a source whose hearing range
     * is {@code maxDistance}. Mirrors what OpenAL computes for the voice.
     */
    public static double curveGain(double distance, double maxDistance, boolean whispering) {
        if (maxDistance <= 0.0) {
            maxDistance = getServerMaxDistance();
        }
        DistanceConfig c = config();
        double rolloff = effectiveRolloff(c, whispering);
        return AudioPhysics.calculateGain(distance / maxDistance, c.getModel(), rolloff,
                c.getMinVolumeFraction(), c.getOpenalReferenceRatio());
    }

    public static double effectiveRolloff(boolean whispering) {
        return effectiveRolloff(config(), whispering);
    }

    public static double effectiveRolloff(DistanceConfig c, boolean whispering) {
        double rolloff = c.getAttenuationFactor();
        if (whispering) {
            rolloff = Math.min(2.0, rolloff * c.getWhisperMultiplier());
        }
        return rolloff;
    }

    // -------------------------------------------------------------------------
    // OpenAL distance curve
    // -------------------------------------------------------------------------

    /**
     * Sets the voice's volume from {@link AudioPhysics}, the same curve the graph draws.
     * <p>
     * OpenAL's own distance models cannot draw these curves (its "exponent" model is a power law
     * that never reaches silence), so the source's distance attenuation is turned off (rolloff 0)
     * and the curve is applied through AL_MAX_GAIN, which OpenAL applies after attenuation.
     * AL_GAIN, which Simple Voice Chat sets every frame to the speaker's volume, is only read:
     * per-player volume and muting keep working, and nothing compounds from frame to frame.
     * Only this source is touched; the context-wide distance model is left alone.
     */
    private static void onOpenALSound(OpenALSoundEvent event) {
        if (event.getPosition() == null) {
            return; // group / static audio has no distance
        }
        CONFIG.ensureLoaded();
        int source = event.getSource();
        DistanceConfig c = config();
        try {
            float maxDist = AL11.alGetSourcef(source, AL11.AL_MAX_DISTANCE);
            boolean whispering = isWhispering(event, maxDist);
            double range = maxDist > 0F ? maxDist : getServerMaxDistance();

            double curve = AudioPhysics.calculateGain(sourceDistance(source) / range, c.getModel(),
                    effectiveRolloff(c, whispering), c.getMinVolumeFraction(), c.getOpenalReferenceRatio());

            float sourceGain = Math.max(0.0F, AL11.alGetSourcef(source, AL11.AL_GAIN));
            AL11.alSourcef(source, AL11.AL_ROLLOFF_FACTOR, 0.0F);
            AL11.alSourcef(source, AL11.AL_MAX_GAIN, (float) (curve * sourceGain));
            // The edge-volume floor scales with the speaker's own volume: muted players stay muted
            AL11.alSourcef(source, AL11.AL_MIN_GAIN, (float) (c.getMinVolumeFraction() * sourceGain));
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Failed to apply OpenAL parameters to source {}: {}", source, t.toString());
        }
    }

    /** Distance between the source and the listener, the way OpenAL measures it. */
    private static double sourceDistance(int source) {
        float[] sx = new float[1];
        float[] sy = new float[1];
        float[] sz = new float[1];
        AL11.alGetSource3f(source, AL11.AL_POSITION, sx, sy, sz);
        double dx = sx[0];
        double dy = sy[0];
        double dz = sz[0];
        if (AL11.alGetSourcei(source, AL11.AL_SOURCE_RELATIVE) == AL11.AL_FALSE) {
            float[] lx = new float[1];
            float[] ly = new float[1];
            float[] lz = new float[1];
            AL11.alGetListener3f(AL11.AL_POSITION, lx, ly, lz);
            dx -= lx[0];
            dy -= ly[0];
            dz -= lz[0];
        }
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean isWhispering(OpenALSoundEvent event, float maxDist) {
        SpeakerRegistry.Speaker speaker = SPEAKERS.get(event.getChannelId());
        if (speaker != null) {
            return speaker.getKind() == SpeakerRegistry.Kind.ENTITY && speaker.isWhispering();
        }
        // Unknown channel (e.g. another addon's audio channel): SVC whispers at half the distance
        return maxDist > 0F && maxDist < getServerMaxDistance() * 0.75;
    }

    // -------------------------------------------------------------------------
    // Wall muffling
    // -------------------------------------------------------------------------

    private static void onEntitySound(ClientReceiveSoundEvent.EntitySound event) {
        captureClientApi(event);
        short[] raw = event.getRawAudio();
        if (raw == null || raw.length == 0 || event.getId() == null) {
            return;
        }
        CONFIG.ensureLoaded();
        SpeakerRegistry.Speaker speaker = SPEAKERS.onEntityFrame(event.getId(), event.getEntityId(),
                event.isWhispering(), event.getDistance(), raw);
        muffle(event, speaker, raw);
    }

    private static void onLocationalSound(ClientReceiveSoundEvent.LocationalSound event) {
        short[] raw = event.getRawAudio();
        Position pos = event.getPosition();
        if (raw == null || raw.length == 0 || pos == null || event.getId() == null) {
            return;
        }
        CONFIG.ensureLoaded();
        SpeakerRegistry.Speaker speaker = SPEAKERS.onLocationalFrame(event.getId(), pos.getX(), pos.getY(), pos.getZ(),
                event.getDistance(), raw);
        muffle(event, speaker, raw);
    }

    /**
     * Everything done to a voice's audio on the client: walls, water and weather as one filter,
     * then the echo of the room the listener is in. Never throws; on a failure the voice plays as it came.
     */
    private static void muffle(ClientReceiveSoundEvent event, SpeakerRegistry.Speaker speaker, short[] raw) {
        try {
            DistanceConfig c = config();
            OcclusionStatus status = occlusionStatus();
            EnvironmentEffects.Effect effect = EnvironmentEffects.Effect.NONE;
            if (status == OcclusionStatus.ACTIVE && speaker.isOcclusionKnown()) {
                double strength = c.getOcclusionStrength();
                effect = new EnvironmentEffects.Effect(OcclusionModel.muffle(speaker.getThickness(), strength),
                        OcclusionModel.lossDb(speaker.getThickness(), strength));
            }
            // Sound Physics Remastered does its own water and echo; weather is ours either way
            boolean ownPhysics = status != OcclusionStatus.SOUND_PHYSICS && status != OcclusionStatus.UNAVAILABLE;
            ListenerEnvironment env = ENVIRONMENT;
            if (ownPhysics && c.isUnderwaterEnabled()) {
                effect = effect.plus(EnvironmentEffects.water(env.isUnderWater(), speaker.isUnderWater()));
            }
            if (c.isWeatherEnabled() && speaker.getDistance() >= 0.0) {
                double range = speaker.getMaxDistance() > 0.0F ? speaker.getMaxDistance() : getServerMaxDistance();
                effect = effect.plus(EnvironmentEffects.weather(
                        ListenerEnvironment.worse(env.weather(), speaker.getWeather()), speaker.getDistance() / range));
            }

            // Both stages work on the frame in place
            boolean changed = false;
            VoiceFilter filter = speaker.getFilter();
            // Zero targets let an engaged filter glide back open instead of cutting off
            if (effect.muffle() > 0.0 || effect.lossDb() > 0.0 || filter.isEngaged()) {
                filter.process(raw, effect.muffle(), effect.lossDb());
                changed = true;
            }
            Reverb reverb = speaker.getReverb();
            RoomEstimate room = env.room();
            double wet = ownPhysics && c.isReverbEnabled() ? room.wet() * c.getReverbStrength() : 0.0;
            if (wet > 0.0 || reverb.isActive()) {
                reverb.process(raw, wet, room.decaySeconds());
                changed = true;
            }
            if (changed) {
                event.setRawAudio(raw);
            }
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Voice processing failed for {}: {}", speaker.getChannelId(), t.toString());
        }
    }
}
