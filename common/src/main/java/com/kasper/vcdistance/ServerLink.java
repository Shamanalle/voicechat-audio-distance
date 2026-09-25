package com.kasper.vcdistance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Client-side view of the server the player is connected to.
 * <p>
 * Stays empty on servers without the addon. When the server has it, it holds the server's profile
 * and how it is offered: {@code suggest} only shows it in the settings screen, {@code enforce}
 * makes it the effective configuration until the player leaves (their own config is never
 * overwritten). It also sends the voice chat state of nearby players for the monitor.
 */
public final class ServerLink {

    /** Nearby states older than this are dropped: the server sends them about once a second. */
    public static final long NEARBY_STALE_NANOS = TimeUnit.SECONDS.toNanos(4);

    private volatile LinkProtocol.ServerProfile profile;
    private volatile boolean noticePending;
    private volatile String zoneNotice;
    private volatile Nearby nearby;

    private record Nearby(Map<UUID, VoiceState> states, long receivedNanos) {
        boolean fresh(long nowNanos) {
            return nowNanos - receivedNanos <= NEARBY_STALE_NANOS;
        }
    }

    /** Handles a {@code profile} message from the server. */
    public void onProfile(String text) {
        LinkProtocol.ServerProfile p = LinkProtocol.parseProfile(text);
        if (p == null) {
            return;
        }
        LinkProtocol.ServerProfile previous = profile;
        profile = p;
        if (p.mode() != ServerSettings.ProfileMode.OFF && (previous == null || previous.mode() != p.mode())) {
            noticePending = true;
        }
        String before = previous == null ? null : previous.zone();
        if (!java.util.Objects.equals(before, p.zone())) {
            // Entering a zone names it; leaving one says the main profile is back ("")
            zoneNotice = p.zone() != null ? p.zone() : (before != null ? "" : null);
        }
    }

    /** Handles a {@code nearby} message from the server. */
    public void onNearby(String text) {
        onNearby(text, System.nanoTime());
    }

    void onNearby(String text, long nowNanos) {
        Map<UUID, VoiceState> states = LinkProtocol.parseNearby(text);
        if (states != null) {
            nearby = new Nearby(states, nowNanos);
        }
    }

    /**
     * Voice chat state of a nearby player, or {@code null} when unknown: the server does not have
     * the addon, did not list the player, or has not sent an update for a while.
     */
    public VoiceState voiceState(UUID player, long nowNanos) {
        Nearby n = nearby;
        return player != null && n != null && n.fresh(nowNanos) ? n.states().get(player) : null;
    }

    /** {@code true} while the server keeps sending nearby states (it has an addon version that does). */
    public boolean hasVoiceStates(long nowNanos) {
        Nearby n = nearby;
        return n != null && n.fresh(nowNanos);
    }

    /** The server's profile, or {@code null} when the server does not have the addon. */
    public LinkProtocol.ServerProfile profile() {
        return profile;
    }

    public boolean isConnected() {
        return profile != null;
    }

    public boolean isEnforced() {
        LinkProtocol.ServerProfile p = profile;
        return p != null && p.mode() == ServerSettings.ProfileMode.ENFORCE;
    }

    public boolean isSuggested() {
        LinkProtocol.ServerProfile p = profile;
        return p != null && p.mode() == ServerSettings.ProfileMode.SUGGEST;
    }

    /** The configuration to use right now: the server's when enforced, otherwise the player's own. */
    public DistanceConfig effective(DistanceConfig own) {
        LinkProtocol.ServerProfile p = profile;
        return p != null && p.mode() == ServerSettings.ProfileMode.ENFORCE ? p.config() : own;
    }

    /** Whisper range as a share of the voice range; 0.5 (Simple Voice Chat's default) when unknown. */
    public double whisperShare() {
        LinkProtocol.ServerProfile p = profile;
        return p != null ? p.whisperShare() : 0.5;
    }

    /** @return {@code true} once after a server started suggesting or enforcing a profile */
    public boolean consumeNotice() {
        if (noticePending) {
            noticePending = false;
            return true;
        }
        return false;
    }

    /**
     * @return the zone just entered, "" when a zone was just left, or {@code null}; once per change
     */
    public String consumeZoneNotice() {
        String z = zoneNotice;
        zoneNotice = null;
        return z;
    }

    /** Called when leaving a server. */
    public void reset() {
        profile = null;
        noticePending = false;
        zoneNotice = null;
        nearby = null;
    }
}
