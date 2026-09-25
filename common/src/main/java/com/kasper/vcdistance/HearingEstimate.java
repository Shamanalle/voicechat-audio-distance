package com.kasper.vcdistance;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Who within your voice range will hear you: players whose voice chat is connected with the sound
 * on count as hearing, those with no voice chat, a dropped connection or the sound off do not, and
 * players whose state is unknown are counted separately.
 */
public record HearingEstimate(int inRange, int hear, int deaf, int unknown) {

    public static final HearingEstimate NONE = new HearingEstimate(0, 0, 0, 0);

    /**
     * @param players nearby players with their distance to you
     * @param range   how far your voice carries right now (the whisper range while whispering)
     * @param states  voice chat state per player, {@code null} when unknown
     */
    public static HearingEstimate of(List<NearbyPlayers.Player> players, double range, Function<UUID, VoiceState> states) {
        int inRange = 0;
        int hear = 0;
        int deaf = 0;
        int unknown = 0;
        for (NearbyPlayers.Player p : players) {
            if (p.distance() > range) {
                continue;
            }
            inRange++;
            VoiceState s = states.apply(p.id());
            if (s == null) {
                unknown++;
            } else if (s.isProblem()) {
                deaf++;
            } else {
                hear++;
            }
        }
        return new HearingEstimate(inRange, hear, deaf, unknown);
    }

    /** {@code true} when every player in range has a known state. */
    public boolean isExact() {
        return unknown == 0;
    }
}
