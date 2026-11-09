package dev.zernyth.api.event;

import dev.zernyth.api.player.Player;

/**
 * Synchronous native quit notification. Do not assume that the player remains online after this callback.
 */
public final class PlayerQuitEvent extends Event {

    /**
     * Associates the player view with a quit notification; this does not keep the session online.
     *
     * @param player the associated session-checked player view
     */
    public PlayerQuitEvent(Player player) {
        super(player);
    }
}
