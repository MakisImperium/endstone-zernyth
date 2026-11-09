package dev.zernyth.api.network.connection;

/**
 * State of the native player-associated view; no fabricated WebRTC states.
 */
public enum ConnectionState {
    /**
     * An active native player association.
     */
    PLAYER_ASSOCIATED,
    /**
     * The player association or form UI has closed, according to the enclosing type.
     */
    CLOSED
}
