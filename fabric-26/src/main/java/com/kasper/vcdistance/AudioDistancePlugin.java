package com.kasper.vcdistance;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import org.lwjgl.openal.AL11;

/**
 * VoiceChat Audio Distance Addon - Core OpenAL Plugin for Minecraft 26.3.
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
        DistanceConfig.LOGGER.info("VoiceChat Audio Distance Addon (Minecraft 26.3) initialized successfully.");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(OpenALSoundEvent.class, this::onOpenALSound);
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, this::onReceiveEntitySound);
        registration.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, this::onReceiveLocationalSound);
    }

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

    private void onOpenALSound(OpenALSoundEvent event) {
        if (event.getPosition() == null) {
            return;
        }

        int source = event.getSource();

        try {
            // 1. Set OpenAL distance attenuation model
            AL11.alDistanceModel(CONFIG.model.getOpenAlConstant());

            // 2. Determine if this source is whispering
            float maxDist = AL11.alGetSourcef(source, AL11.AL_MAX_DISTANCE);
            float serverDist = (float) getServerMaxDistance();
            boolean isWhisper = (maxDist > 0F && maxDist < serverDist * 0.75F);

            // 3. Compute rolloff factor
            float rolloff = (float) CONFIG.attenuationFactor;
            if (isWhisper && CONFIG.whisperMultiplier != 1.0) {
                rolloff = (float) Math.max(0.01, Math.min(2.0, rolloff * CONFIG.whisperMultiplier));
            }
            AL11.alSourcef(source, AL11.AL_ROLLOFF_FACTOR, rolloff);

            // 4. Hardware volume floor (AL_MIN_GAIN)
            float currentGain = AL11.alGetSourcef(source, AL11.AL_GAIN);
            if (currentGain <= 0.0001F) {
                AL11.alSourcef(source, AL11.AL_MIN_GAIN, 0.0F);
            } else {
                float effectiveMinGain = (float) Math.max(0.0, Math.min(currentGain, CONFIG.minVolumeFraction * currentGain));
                AL11.alSourcef(source, AL11.AL_MIN_GAIN, effectiveMinGain);
            }

            // 5. Reference distance
            if (maxDist > 0F) {
                float refDist = maxDist * (float) Math.max(0.05, Math.min(1.0, CONFIG.openalReferenceRatio));
                AL11.alSourcef(source, AL11.AL_REFERENCE_DISTANCE, refDist);
            }
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Failed to apply OpenAL parameters on source {}: {}", source, t.getMessage());
        }
    }

    private void onReceiveEntitySound(ClientReceiveSoundEvent.EntitySound event) {
        if (!CONFIG.occlusionEnabled || CONFIG.occlusionStrength <= 0.001) {
            return;
        }
        short[] raw = event.getRawAudio();
        if (raw == null || raw.length == 0) {
            OcclusionFilter.reset(event.getId());
            return;
        }

        try {
            double occlusion = RaycastOcclusion.getEntityOcclusion(event.getEntityId(), event.getDistance());
            short[] processed = OcclusionFilter.processPcm(event.getId(), raw, occlusion, CONFIG.occlusionStrength);
            event.setRawAudio(processed);
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Error during entity sound occlusion processing: {}", t.getMessage());
        }
    }

    private void onReceiveLocationalSound(ClientReceiveSoundEvent.LocationalSound event) {
        if (!CONFIG.occlusionEnabled || CONFIG.occlusionStrength <= 0.001) {
            return;
        }
        short[] raw = event.getRawAudio();
        if (raw == null || raw.length == 0) {
            OcclusionFilter.reset(event.getId());
            return;
        }

        try {
            Position pos = event.getPosition();
            if (pos == null) {
                return;
            }
            double occlusion = RaycastOcclusion.getLocationalOcclusion(event.getId(), pos.getX(), pos.getY(), pos.getZ());
            short[] processed = OcclusionFilter.processPcm(event.getId(), raw, occlusion, CONFIG.occlusionStrength);
            event.setRawAudio(processed);
        } catch (Throwable t) {
            DistanceConfig.LOGGER.debug("Error during locational sound occlusion processing: {}", t.getMessage());
        }
    }
}
