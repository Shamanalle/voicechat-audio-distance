package com.kasper.vcdistance;

/**
 * Who hears whom, and how far a voice carries, under the server's voice rules: zone ranges
 * (a stage, a library), isolated zones, sneaking, a megaphone, silent dead players and spectators
 * who only hear each other. Simple Voice Chat decides who is close enough by its own range first;
 * this narrows that down, or (for longer ranges) tells the server whom else to send the voice to.
 * <p>
 * Pure logic over {@link ServerPlayers.Info}, so it runs on Simple Voice Chat's thread without
 * touching the world.
 */
public final class ServerRange {

    /** Why a listener does not hear a voice (for {@code /vcd debug}), or {@link #HEARS}. */
    public enum Reason {
        HEARS, RANGE, ISOLATED, DEAD, SPECTATOR, WORLD
    }

    /**
     * @param reason   {@link Reason#HEARS} when the voice reaches the listener
     * @param distance the voice's range in blocks, for the packet (the client fades the voice over it)
     */
    public record Decision(Reason reason, double distance) {

        public boolean hears() {
            return reason == Reason.HEARS;
        }
    }

    private ServerRange() {
    }

    /**
     * How far {@code speaker}'s voice carries, in blocks.
     *
     * @param voice   Simple Voice Chat's voice range
     * @param whisper Simple Voice Chat's whisper range
     */
    public static double rangeOf(ServerSettings s, ServerPlayers.Info speaker, boolean whispering, double voice, double whisper) {
        double range = whispering ? whisper : voice;
        Zone zone = s.zoneOf(speaker);
        if (zone != null) {
            Zone.Rules rules = zone.rules();
            Double fixed = whispering ? rules.whisperRange() : rules.voiceRange();
            if (fixed != null) {
                range = fixed;
            } else if (whispering && rules.voiceRange() != null && voice > 0.0) {
                // A zone that only sets the voice range scales whispers the same way
                range = whisper * rules.voiceRange() / voice;
            } else if (rules.rangeMultiplier() != null) {
                range *= rules.rangeMultiplier();
            }
        }
        if (speaker.sneaking()) {
            range *= s.getSneakMultiplier();
        }
        if (isMegaphone(s, speaker)) {
            range *= s.getMegaphoneMultiplier();
        }
        return range;
    }

    public static boolean isMegaphone(ServerSettings s, ServerPlayers.Info speaker) {
        return !s.getMegaphoneItem().isEmpty() && speaker.holds(s.getMegaphoneItem());
    }

    /**
     * Whether {@code listener} hears {@code speaker} in their Simple Voice Chat group. Only the rules
     * the admin chose for groups apply; a group has no range, so the distance is 0.
     */
    public static Decision decideGroup(ServerSettings s, ServerPlayers.Info speaker, ServerPlayers.Info listener) {
        if (s.isGroupDeadSilent() && !speaker.alive()) {
            return new Decision(Reason.DEAD, 0.0);
        }
        if (s.isGroupSpectatorsApart() && speaker.spectator() && !listener.spectator()) {
            return new Decision(Reason.SPECTATOR, 0.0);
        }
        if (s.isGroupIsolatedZones()) {
            Zone from = s.zoneOf(speaker);
            Zone to = s.zoneOf(listener);
            boolean sameZone = from == null ? to == null : to != null && from.key().equals(to.key());
            if (!sameZone && ((from != null && from.rules().isolated()) || (to != null && to.rules().isolated()))) {
                return new Decision(Reason.ISOLATED, 0.0);
            }
        }
        return new Decision(Reason.HEARS, 0.0);
    }

    /** Whether {@code listener} hears {@code speaker}, and over what range. */
    public static Decision decide(ServerSettings s, ServerPlayers.Info speaker, ServerPlayers.Info listener,
                                  boolean whispering, double voice, double whisper) {
        double range = rangeOf(s, speaker, whispering, voice, whisper);
        if (s.isDeadSilent() && !speaker.alive()) {
            return new Decision(Reason.DEAD, range);
        }
        if (s.isSpectatorsOnly() && speaker.spectator() && !listener.spectator()) {
            return new Decision(Reason.SPECTATOR, range);
        }
        if (speaker.world() == null || listener.world() == null || !Zone.sameWorld(speaker.world(), listener.world())) {
            return new Decision(Reason.WORLD, range);
        }
        Zone from = s.zoneOf(speaker);
        Zone to = s.zoneOf(listener);
        boolean sameZone = from == null ? to == null : to != null && from.key().equals(to.key());
        if (!sameZone && ((from != null && from.rules().isolated()) || (to != null && to.rules().isolated()))) {
            return new Decision(Reason.ISOLATED, range);
        }
        if (speaker.distanceTo(listener) > range) {
            return new Decision(Reason.RANGE, range);
        }
        return new Decision(Reason.HEARS, range);
    }
}
