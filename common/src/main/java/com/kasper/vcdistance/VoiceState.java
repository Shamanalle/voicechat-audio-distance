package com.kasper.vcdistance;

/**
 * Voice chat state of a nearby player, as the server's Simple Voice Chat knows it. Only a server
 * with the addon sends these; without it the client cannot tell them apart and shows none.
 */
public enum VoiceState {
    /** Connected, sound on: hears you. */
    CONNECTED("ok"),
    /** In a Simple Voice Chat group; depending on the group type they may not hear nearby players. */
    GROUP("group"),
    /** Turned the voice chat sound off: does not hear anyone. */
    SOUND_OFF("off"),
    /** Has Simple Voice Chat, but its voice connection is down. */
    DISCONNECTED("disconnected"),
    /** No (compatible) Simple Voice Chat installed. */
    NO_VOICE_CHAT("none");

    private final String id;

    VoiceState(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /** Key suffix for the translations ({@code monitor.state.<key>}). */
    public String getTranslationKey() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** {@code true} when this player will not hear nearby voices. */
    public boolean isProblem() {
        return this == SOUND_OFF || this == DISCONNECTED || this == NO_VOICE_CHAT;
    }

    /** @return the state with this wire id, or {@code null} when unknown */
    public static VoiceState fromId(String id) {
        for (VoiceState s : values()) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    /**
     * State from the fields of Simple Voice Chat's {@code VoicechatConnection}. A player without the
     * mod whom another plugin shows as connected (a voice bridge) counts as connected.
     */
    public static VoiceState of(boolean installed, boolean connected, boolean disabled, boolean inGroup) {
        if (!connected) {
            return installed ? DISCONNECTED : NO_VOICE_CHAT;
        }
        if (disabled) {
            return SOUND_OFF;
        }
        return inGroup ? GROUP : CONNECTED;
    }
}
