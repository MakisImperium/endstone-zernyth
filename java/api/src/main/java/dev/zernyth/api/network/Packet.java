package dev.zernyth.api.network;

/**
 * Metadata only. No payload, decoded fields, mutation or native packet ownership.
 */
public sealed interface Packet permits PacketSnapshot {

    /**
     * Returns packet type metadata.
     *
     * @return the packet type
     */
    PacketType type();

    /**
     * Returns packet direction relative to the server.
     *
     * @return the packet direction
     */
    PacketDirection direction();

    /**
     * Returns the observation protocol.
     *
     * @return the packet protocol
     */
    NetworkProtocol protocol();

    /**
     * Returns the detached processing context.
     *
     * @return the detached packet context
     */
    PacketContext context();

    /**
     * Returns the observed serialized payload byte count, without exposing bytes.
     *
     * @return the observed nonnegative payload byte count
     */
    long payloadSize();
}
