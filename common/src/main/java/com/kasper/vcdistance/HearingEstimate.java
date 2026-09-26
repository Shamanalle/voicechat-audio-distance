package com.kasper.vcdistance;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Who will hear you: players within your voice range whose voice chat is connected with the sound
 * on count as hearing, those with no voice chat, a dropped connection or the sound off do not, and
 * players whose state is unknown are counted separately. In a Simple Voice Chat group the group's
 * members count too, wherever they are ({@code group} of them).
 */
public record HearingEstimate(int inRange, int hear, int deaf, int unknown, int group) {

    public static final HearingEstimate NONE = new HearingEstimate(0, 0, 0, 0, 0);

    public HearingEstimate(int inRange, int hear, int deaf, int unknown) {
        this(inRange, hear, deaf, unknown, 0);
    }

    /**
     * @param players nearby players with their distance to you
     * @param range   how far your voice carries right now (the whisper range while whispering)
     * @param states  voice chat state per player, {@code null} when unknown
     */
    public static HearingEstimate of(List<NearbyPlayers.Player> players, double range, Function<UUID, VoiceState> states) {
        return of(players, range, states, null, LinkProtocol.GroupInfo.NONE);
    }

    /**
     * Simple Voice Chat's group rules: members hear each other anywhere; players nearby hear a
     * member only when the group is open; a player in an isolated group hears only their group.
     *
     * @param selfGroupType your group's type ("normal", "open", "isolated", "" when unknown), or
     *                      {@code null} when you are in none
     * @param group         what the server said about your group ({@link LinkProtocol.GroupInfo#NONE} when nothing)
     */
    public static HearingEstimate of(List<NearbyPlayers.Player> players, double range, Function<UUID, VoiceState> states,
                                     String selfGroupType, LinkProtocol.GroupInfo group) {
        // Only an open group is heard by the players around it
        boolean nearbyHears = selfGroupType == null || "open".equals(selfGroupType);
        int inRange = 0;
        int hear = 0;
        int deaf = 0;
        int unknown = 0;
        if (nearbyHears) {
            for (NearbyPlayers.Player p : players) {
                if (p.distance() > range) {
                    continue;
                }
                if (selfGroupType != null && group.mates().contains(p.id())) {
                    continue; // counted with the group
                }
                inRange++;
                VoiceState s = states.apply(p.id());
                if (s == null) {
                    unknown++;
                } else if (s.isProblem() || group.isolated().contains(p.id())) {
                    deaf++;
                } else {
                    hear++;
                }
            }
        }
        int members = 0;
        if (selfGroupType != null && group.hasTotals()) {
            members = group.groupHear() + group.groupDeaf();
            inRange += members;
            hear += group.groupHear();
            deaf += group.groupDeaf();
        }
        return new HearingEstimate(inRange, hear, deaf, unknown, members);
    }

    /** {@code true} when every player in range has a known state. */
    public boolean isExact() {
        return unknown == 0;
    }
}
