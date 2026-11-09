package dev.zernyth.api.network;

import dev.zernyth.api.network.connection.ConnectionInfo;

/**
 * Player-associated lifetime view, NOT a WebRTC/ICE or unauthenticated transport session.
 */
public interface NetworkConnection {

    /**
     * Reads detached connection information with current activity validation.
     *
     * @return detached connection information
     */
    ConnectionInfo getInfo();

    /**
     * Checks whether the original player connection association remains valid.
     *
     * @return whether the original association remains active
     */
    boolean isActive();
}
