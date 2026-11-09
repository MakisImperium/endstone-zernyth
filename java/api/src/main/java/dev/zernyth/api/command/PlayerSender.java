package dev.zernyth.api.command;

import dev.zernyth.api.player.Player;

/**
 * Player-associated command sender. Resolve the separately session-checked player during the native command
 * callback.
 */
public interface PlayerSender extends CommandSender {

    /**
     * Returns the associated player view, whose native access still validates its session.
     *
     * @return the associated session-checked player view
     */
    Player getPlayer();
}
