package com.kasper.vcdistance;

/**
 * Client-side view of the server the player is connected to.
 * <p>
 * Stays empty on servers without the addon. When the server has it, it holds the server's profile
 * and how it is offered: {@code suggest} only shows it in the settings screen, {@code enforce}
 * makes it the effective configuration until the player leaves (their own config is never
 * overwritten).
 */
public final class ServerLink {

    private volatile LinkProtocol.ServerProfile profile;
    private volatile boolean noticePending;

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

    /** Called when leaving a server. */
    public void reset() {
        profile = null;
        noticePending = false;
    }
}
