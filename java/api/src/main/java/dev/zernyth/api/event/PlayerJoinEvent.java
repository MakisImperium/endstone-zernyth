package dev.zernyth.api.event;

import dev.zernyth.api.player.Player;

/**
 * Synchronous native join notification. The callback-scoped event and session-checked player have distinct
 * lifetimes.
 */
public final class PlayerJoinEvent extends Event {

    /**
     * Associates the session-checked player view with this join notification.
     *
     * @param player the associated session-checked player view
     */
    public PlayerJoinEvent(Player player) {
        super(player);
    }
}
