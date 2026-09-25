package com.kasper.vcdistance;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import org.lwjgl.openal.AL11;

/**
 * Simple Voice Chat plugin, on both sides.
 * <p>
 * Client: shapes the OpenAL distance curve of every positional voice and muffles voices heard
 * through walls. Server: muffles voices through walls for players who do not have the addon
 * ({@link ServerWalls}).
 * <p>
 * This class only depends on the SVC API and LWJGL, so the same code runs on every Minecraft
 * version and loader. World access (raycasts, entity lookups) lives in the glue of each version,
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
    /** Server-side settings and wall muffling. */
    public static final ServerSettings SERVER_SETTINGS = new ServerSettings();
    public static final ServerWalls SERVER_WALLS = new ServerWalls(SERVER_SETTINGS);

    private static volatile VoicechatApi api;
    private static volatile VoicechatServerApi serverApi;
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
        registration.registerEvent(PlayerDisconnectedEvent.class, e -> SERVER_WALLS.forgetPlayer(e.getPlayerUuid()));
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

    private static void onOpenALSound(OpenALSoundEvent event) {
        if (event.getPosition() == null) {
            return; // group / static audio has no distance
        }
        CONFIG.ensureLoaded();
        int source = event.getSource();
        DistanceConfig c = config();
        try {
            // SVC resets the (context-wide) model to AL_LINEAR_DISTANCE on every frame
            AL11.alDistanceModel(c.getModel().getOpenAlConstant());

            float maxDist = AL11.alGetSourcef(source, AL11.AL_MAX_DISTANCE);
            boolean whispering = isWhispering(event, maxDist);

            AL11.alSourcef(source, AL11.AL_ROLLOFF_FACTOR, (float) Math.max(0.0, effectiveRolloff(c, whispering)));

            if (maxDist > 0F) {
                float ref = maxDist * (float) c.getOpenalReferenceRatio();
                AL11.alSourcef(source, AL11.AL_REFERENCE_DISTANCE, Math.max(0.01F, ref));
            }

            // AL_MIN_GAIN is an absolute clamp, so scale it by the speaker's own volume:
            // muted or turned-down players stay muted / quiet.
            float sourceGain = AL11.alGetSourcef(source, AL11.AL_GAIN);
            float floor = sourceGain <= 0.0001F ? 0.0F : (float) (c.getMinVolumeFraction() * sourceGain);
            AL11.alSourcef(source, AL11.AL_MIN_GAIN, floor);
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Failed to apply OpenAL parameters to source {}: {}", source, t.toString());
        }
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

    private static void muffle(ClientReceiveSoundEvent event, SpeakerRegistry.Speaker speaker, short[] raw) {
        try {
            double muffle = 0.0;
            double lossDb = 0.0;
            if (occlusionStatus() == OcclusionStatus.ACTIVE && speaker.isOcclusionKnown()) {
                double strength = config().getOcclusionStrength();
                muffle = OcclusionModel.muffle(speaker.getThickness(), strength);
                lossDb = OcclusionModel.lossDb(speaker.getThickness(), strength);
            }
            VoiceFilter filter = speaker.getFilter();
            // Zero targets let an engaged filter glide back open instead of cutting off
            if (muffle > 0.0 || lossDb > 0.0 || filter.isEngaged()) {
                event.setRawAudio(filter.process(raw, muffle, lossDb));
            }
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Wall muffling failed for {}: {}", speaker.getChannelId(), t.toString());
        }
    }
}
