package com.kasper.vcdistance;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import org.lwjgl.openal.AL11;

/**
 * Simple Voice Chat plugin: shapes the OpenAL distance curve of every positional voice and
 * muffles voices heard through walls.
 * <p>
 * This class only depends on the SVC API and LWJGL, so the same code runs on every Minecraft
 * version and loader. World access (raycasts, entity lookups) lives in the client glue of each
 * version, which feeds {@link #SPEAKERS} from the main thread.
 */
@ForgeVoicechatPlugin
public class AudioDistancePlugin implements VoicechatPlugin {

    public static final String MOD_ID = "vc-audio-distance";
    public static final double FALLBACK_DISTANCE = 48.0;

    public static final DistanceConfig CONFIG = new DistanceConfig();
    public static final SpeakerRegistry SPEAKERS = new SpeakerRegistry();

    private static volatile VoicechatApi api;
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
        CONFIG.ensureLoaded();
        DistanceConfig.LOGGER.info("VoiceChat Audio Distance initialized (walls: {})", occlusionStatus());
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        // The base event fires after SVC has positioned the source and set its own linear curve.
        registration.registerEvent(OpenALSoundEvent.class, AudioDistancePlugin::onOpenALSound);
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, AudioDistancePlugin::onEntitySound);
        registration.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, AudioDistancePlugin::onLocationalSound);
    }

    // -------------------------------------------------------------------------
    // Public state
    // -------------------------------------------------------------------------

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
        return CONFIG.isOcclusionEnabled() ? OcclusionStatus.ACTIVE : OcclusionStatus.OFF;
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
        double rolloff = effectiveRolloff(whispering);
        return AudioPhysics.calculateGain(distance / maxDistance, CONFIG.getModel(), rolloff,
                CONFIG.getMinVolumeFraction(), CONFIG.getOpenalReferenceRatio());
    }

    public static double effectiveRolloff(boolean whispering) {
        double rolloff = CONFIG.getAttenuationFactor();
        if (whispering) {
            rolloff = Math.min(2.0, rolloff * CONFIG.getWhisperMultiplier());
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
        int source = event.getSource();
        try {
            // SVC resets the (context-wide) model to AL_LINEAR_DISTANCE on every frame
            AL11.alDistanceModel(CONFIG.getModel().getOpenAlConstant());

            float maxDist = AL11.alGetSourcef(source, AL11.AL_MAX_DISTANCE);
            boolean whispering = isWhispering(event, maxDist);

            AL11.alSourcef(source, AL11.AL_ROLLOFF_FACTOR, (float) Math.max(0.0, effectiveRolloff(whispering)));

            if (maxDist > 0F) {
                float ref = maxDist * (float) CONFIG.getOpenalReferenceRatio();
                AL11.alSourcef(source, AL11.AL_REFERENCE_DISTANCE, Math.max(0.01F, ref));
            }

            // AL_MIN_GAIN is an absolute clamp, so scale it by the speaker's own volume:
            // muted or turned-down players stay muted / quiet.
            float sourceGain = AL11.alGetSourcef(source, AL11.AL_GAIN);
            float floor = sourceGain <= 0.0001F ? 0.0F : (float) (CONFIG.getMinVolumeFraction() * sourceGain);
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
        SpeakerRegistry.Speaker speaker = SPEAKERS.onLocationalFrame(event.getId(), pos.getX(), pos.getY(), pos.getZ(),
                event.getDistance(), raw);
        muffle(event, speaker, raw);
    }

    private static void muffle(ClientReceiveSoundEvent event, SpeakerRegistry.Speaker speaker, short[] raw) {
        try {
            double muffle = 0.0;
            double lossDb = 0.0;
            if (occlusionStatus() == OcclusionStatus.ACTIVE && speaker.isOcclusionKnown()) {
                double strength = CONFIG.getOcclusionStrength();
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
