package dev.zernyth.api.network.connection;

import java.util.Objects;
import java.util.UUID;

/**
 * Plugin-scoped opaque identifier; no address, authentication or session secrets.
 *
 * @param identifier the native identifier scoped to its world/protocol
 * @param playerId the player UUID when safely available
 * @param state the observed player-associated connection state
 */
public record ConnectionInfo(String identifier, UUID playerId, ConnectionState state) {

    /**
     * Requires a detached identifier, player UUID and observed state without retaining the native connection.
     *
     * @param identifier the native identifier scoped to its world/protocol
     * @param playerId the player UUID when safely available
     * @param state the observed player-associated connection state
     */
    public ConnectionInfo {
        Objects.requireNonNull(identifier);
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(state);
    }
}
