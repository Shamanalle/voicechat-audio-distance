package com.kasper.vcdistance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance, click-free DSP Low-Pass Filter engine for voice sound occlusion.
 * <p>
 * Simulates acoustic transmission loss through physical barriers using a 1-pole IIR filter.
 * Preserves filter state across 20ms PCM audio frames per channel to eliminate any audio pops or clicks.
 */
public class OcclusionFilter {

    public static final int SAMPLE_RATE = 48000;
    public static final double MAX_CUTOFF_HZ = 18000.0;
    public static final double MIN_CUTOFF_HZ = 500.0;
    private static final long STREAM_TIMEOUT_MS = 4000L;

    private static final Map<UUID, FilterState> STREAM_STATES = new ConcurrentHashMap<>();
    private static volatile long lastPruneTime = System.currentTimeMillis();

    /**
     * Per-channel filter memory to maintain continuous audio waveforms between frames.
     */
    public static class FilterState {
        public float lastY = 0.0F;
        public float currentAlpha = 1.0F;
        public long lastActiveTime = System.currentTimeMillis();

        public FilterState() {}

        public FilterState(float lastY, float currentAlpha) {
            this.lastY = lastY;
            this.currentAlpha = currentAlpha;
            this.lastActiveTime = System.currentTimeMillis();
        }
    }

    /**
     * Resets filter memory for a specific audio channel (e.g. on stream end or disconnect).
     */
    public static void reset(UUID channelId) {
        if (channelId != null) {
            STREAM_STATES.remove(channelId);
        }
    }

    /**
     * Clears all stream states.
     */
    public static void clearAll() {
        STREAM_STATES.clear();
    }

    /**
     * Applies dynamic acoustic low-pass filtering and volume dampening to raw 16-bit PCM frames.
     *
     * @param channelId        Unique stream identifier (usually audio channel UUID or sender UUID)
     * @param pcm              Raw 16-bit mono PCM samples (48kHz)
     * @param occlusionFactor  Physical occlusion fraction between listener and sound (0.0 = clear, 1.0 = fully blocked)
     * @param occlusionStrength User configuration multiplier (0.0 = disabled, 1.0 = maximum effect)
     * @return Processed PCM sample array (modified in-place for zero-allocation performance)
     */
    public static short[] processPcm(UUID channelId, short[] pcm, double occlusionFactor, double occlusionStrength) {
        if (pcm == null || pcm.length == 0) {
            if (channelId != null) {
                STREAM_STATES.remove(channelId);
            }
            return pcm;
        }

        double effectiveOcclusion = Math.max(0.0, Math.min(1.0, occlusionFactor * occlusionStrength));

        FilterState state = null;
        if (channelId != null) {
            state = STREAM_STATES.computeIfAbsent(channelId, id -> new FilterState());
            state.lastActiveTime = System.currentTimeMillis();
        }

        // Fast-path bypass when there is no occlusion and filter state has returned to neutral
        if (effectiveOcclusion <= 0.001) {
            if (state != null) {
                if (Math.abs(state.lastY) < 1.0F && state.currentAlpha >= 0.99F) {
                    state.lastY = 0.0F;
                    state.currentAlpha = 1.0F;
                    return pcm;
                }
            } else {
                return pcm;
            }
        }

        // Calculate dynamic cutoff frequency: exponential sweep from MAX_CUTOFF down to MIN_CUTOFF
        double cutoffHz = calculateCutoffFrequency(effectiveOcclusion);

        // Alpha calculation for 1st-order IIR low-pass: α = 1 - e^(-2π * fc / fs)
        float targetAlpha = (float) (1.0 - Math.exp(-2.0 * Math.PI * cutoffHz / SAMPLE_RATE));
        targetAlpha = Math.max(0.02F, Math.min(1.0F, targetAlpha));

        // Additional gain absorption through solid barriers (down to -3 dB at full obstruction)
        float targetGain = (float) (1.0 - 0.25 * effectiveOcclusion);

        float lastY = state != null ? state.lastY : 0.0F;
        float alpha = state != null ? state.currentAlpha : targetAlpha;

        // Sample-rate rate-of-change limit for alpha to ensure smooth parameter transitions
        float alphaStep = (targetAlpha - alpha) * 0.005F;

        for (int i = 0; i < pcm.length; i++) {
            if (Math.abs(targetAlpha - alpha) > 0.0001F) {
                alpha += alphaStep;
            }

            float inputSample = (float) pcm[i] * targetGain;
            float y = lastY + alpha * (inputSample - lastY);
            lastY = y;

            int clamped = Math.round(y);
            if (clamped > 32767) {
                clamped = 32767;
            } else if (clamped < -32768) {
                clamped = -32768;
            }
            pcm[i] = (short) clamped;
        }

        if (state != null) {
            state.lastY = lastY;
            state.currentAlpha = alpha;
        }

        maybePruneInactiveStreams();
        return pcm;
    }

    /**
     * Maps an occlusion fraction [0.0, 1.0] to a low-pass cutoff frequency in Hertz.
     */
    public static double calculateCutoffFrequency(double occlusionFraction) {
        double clamped = Math.max(0.0, Math.min(1.0, occlusionFraction));
        // Exponential frequency scale: fc(O) = f_max * (f_min / f_max)^O
        return MAX_CUTOFF_HZ * Math.pow(MIN_CUTOFF_HZ / MAX_CUTOFF_HZ, clamped);
    }

    /**
     * Periodic garbage collection for inactive stream filter states.
     */
    private static void maybePruneInactiveStreams() {
        long now = System.currentTimeMillis();
        if (now - lastPruneTime > 5000L) {
            lastPruneTime = now;
            STREAM_STATES.entrySet().removeIf(entry -> now - entry.getValue().lastActiveTime > STREAM_TIMEOUT_MS);
        }
    }
}
