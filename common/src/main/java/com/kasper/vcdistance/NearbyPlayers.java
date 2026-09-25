package com.kasper.vcdistance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Players within voice range of the listener, for the monitor. The client tick fills it from the
 * world (so it works on any server); the monitor merges it with the voices being heard and, when
 * the server has the addon, with each player's voice chat state.
 */
public final class NearbyPlayers {

    /** A player the client can see within voice range. */
    public record Player(UUID id, String name, double distance) {
    }

    /**
     * One line of the monitor: a voice being heard ({@code speaker} set), or a nearby player who is
     * not talking. {@code state} is {@code null} when the server did not say.
     */
    public record Row(SpeakerRegistry.Speaker speaker, UUID playerId, String name, double distance, VoiceState state) {

        public boolean isTalking() {
            return speaker != null;
        }
    }

    private volatile List<Player> players = List.of();

    /** Replaces the list (client main thread). */
    public void update(List<Player> nearby) {
        List<Player> sorted = new ArrayList<>(nearby);
        sorted.sort(Comparator.comparingDouble(Player::distance));
        players = List.copyOf(sorted);
    }

    /** Nearby players, closest first. */
    public List<Player> players() {
        return players;
    }

    public void clear() {
        players = List.of();
    }

    /**
     * The monitor's lines: everyone talking (closest first, as {@code active} is sorted), then the
     * other nearby players by distance.
     *
     * @param active voices heard right now, closest first
     * @param nearby nearby players, closest first
     * @param states voice chat state per player; returns {@code null} when unknown
     */
    public static List<Row> rows(List<SpeakerRegistry.Speaker> active, List<Player> nearby,
                                 Function<UUID, VoiceState> states) {
        List<Row> rows = new ArrayList<>(active.size() + nearby.size());
        Set<UUID> talking = new HashSet<>();
        for (SpeakerRegistry.Speaker s : active) {
            UUID player = s.getKind() == SpeakerRegistry.Kind.ENTITY ? s.getEntityId() : null;
            if (player != null) {
                talking.add(player);
            }
            rows.add(new Row(s, player, s.getDisplayName(), s.getDistance(), player != null ? states.apply(player) : null));
        }
        for (Player p : nearby) {
            if (!talking.contains(p.id())) {
                rows.add(new Row(null, p.id(), p.name(), p.distance(), states.apply(p.id())));
            }
        }
        return rows;
    }
}
