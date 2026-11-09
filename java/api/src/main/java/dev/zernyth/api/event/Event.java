package dev.zernyth.api.event;

import java.util.Objects;

import dev.zernyth.api.player.Player;

/**
 * A native event view. Native access expires when this individual listener returns.
 */
public abstract class Event implements EventView {

    private final Player player;

    /**
     * Requires the associated player view without extending its native session lifetime.
     *
     * @param player the associated session-checked player view
     */
    protected Event(Player player) {
        this.player = Objects.requireNonNull(player);
    }

    /**
     * Returns the associated player view, whose native access still validates its session.
     *
     * @return the associated session-checked player view
     */
    public final Player getPlayer() {
        return player;
    }
}
