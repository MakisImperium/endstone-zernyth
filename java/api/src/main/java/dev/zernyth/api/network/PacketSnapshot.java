package dev.zernyth.api.network;

import java.util.Objects;

/**
 * Independent immutable value; remains readable after callback/disable/disconnect.
 *
 * @param type the required class or native type identifier, according to the signature
 * @param direction the direction relative to this server
 * @param protocol the packet observation protocol
 * @param context the detached observation or parsed command context
 * @param payloadSize the nonnegative observed serialized payload byte count
 */
public record PacketSnapshot(PacketType type, PacketDirection direction, NetworkProtocol protocol,
                             PacketContext context, long payloadSize) implements Packet {

    /**
     * Rejects negative payload sizes or mismatched type/protocol versions and requires non-null metadata
     * values.
     *
     * @param type the required class or native type identifier, according to the signature
     * @param direction the direction relative to this server
     * @param protocol the packet observation protocol
     * @param context the detached observation or parsed command context
     * @param payloadSize the nonnegative observed serialized payload byte count
     */
    public PacketSnapshot {
        Objects.requireNonNull(type);
        Objects.requireNonNull(direction);
        Objects.requireNonNull(protocol);
        Objects.requireNonNull(context);
        if (payloadSize < 0 || type.protocolVersion() != protocol.protocolVersion()) {
            throw new IllegalArgumentException("Invalid packet metadata");
        }
    }
}
