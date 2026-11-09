package dev.zernyth.api.network;

/**
 * Packet direction relative to this server, independent of transport internals.
 */
public enum PacketDirection {
    /**
     * Client-to-server observation.
     */
    INBOUND,
    /**
     * Server-to-client observation.
     */
    OUTBOUND
}
