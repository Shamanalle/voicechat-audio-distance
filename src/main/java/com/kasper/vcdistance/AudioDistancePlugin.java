package com.kasper.vcdistance;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import org.lwjgl.openal.AL11;

/**
 * VoiceChat Audio Distance Addon - Core OpenAL Plugin.
 * <p>
 * Controls 3D audio attenuation curves, hardware volume floor, and reference distances
 * through the Simple Voice Chat API and OpenAL 1.1.
 */
public class AudioDistancePlugin implements VoicechatPlugin {

    public static final DistanceConfig CONFIG = new DistanceConfig();
    public static VoicechatApi VOICECHAT_API;

    @Override
    public String getPluginId() {
        return "vc-audio-distance";
    }

    @Override
    public void initialize(VoicechatApi api) {
        VOICECHAT_API = api;
        CONFIG.load();
        DistanceConfig.LOGGER.info("VoiceChat Audio Distance Addon initialized successfully.");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(OpenALSoundEvent.class, this::onOpenALSound);
    }

    /**
     * Returns the maximum voice distance configured on the connected server (in blocks).
     * Defaults to 48 blocks if not connected or unavailable.
     */
    public static double getServerMaxDistance() {
        if (VOICECHAT_API != null) {
            try {
                double dist = VOICECHAT_API.getVoiceChatDistance();
                if (dist > 0.0) {
                    return dist;
                }
            } catch (Throwable ignored) {
            }
        }
        return 48.0;
    }

    /**
     * Applies configured attenuation model, rolloff factor, reference distance,
     * and minimum volume floor directly to the OpenAL source.
     */
    private void onOpenALSound(OpenALSoundEvent event) {
        if (event.getPosition() == null) {
            return;
        }

        int source = event.getSource();

        try {
            // 1. Set OpenAL distance attenuation model (Linear, Inverse, or Exponential)
            AL11.alDistanceModel(CONFIG.model.getOpenAlConstant());

            // 2. Determine if this source is whispering (SVC sets whisper distance to ~half of voice distance)
            float maxDist = AL11.alGetSourcef(source, AL11.AL_MAX_DISTANCE);
            float serverDist = (float) getServerMaxDistance();
            boolean isWhisper = (maxDist > 0F && maxDist < serverDist * 0.75F);

            // 3. Compute rolloff factor (applying whisper multiplier if whispering)
            float rolloff = (float) CONFIG.attenuationFactor;
            if (isWhisper && CONFIG.whisperMultiplier != 1.0) {
                rolloff = (float) Math.max(0.01, Math.min(2.0, rolloff * CONFIG.whisperMultiplier));
            }
            AL11.alSourcef(source, AL11.AL_ROLLOFF_FACTOR, rolloff);

            // 4. Hardware volume floor (AL_MIN_GAIN)
            // Scaled relative to current source AL_GAIN so muted or quieted players are never overridden
            float currentGain = AL11.alGetSourcef(source, AL11.AL_GAIN);
            if (currentGain <= 0.0001F) {
                AL11.alSourcef(source, AL11.AL_MIN_GAIN, 0.0F);
            } else {
                float effectiveMinGain = (float) Math.max(0.0, Math.min(currentGain, CONFIG.minVolumeFraction * currentGain));
                AL11.alSourcef(source, AL11.AL_MIN_GAIN, effectiveMinGain);
            }

            // 5. Reference distance (point where attenuation begins)
            if (maxDist > 0F) {
                float refDist = maxDist * (float) Math.max(0.05, Math.min(1.0, CONFIG.openalReferenceRatio));
                AL11.alSourcef(source, AL11.AL_REFERENCE_DISTANCE, refDist);
            }
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Failed to apply OpenAL parameters on source {}: {}", source, t.getMessage());
        }
    }
}
