package dev.zernyth.api.network;

import java.util.Objects;

/**
 * IDs are supplied by native headers at runtime, never assumed portable across protocols.
 *
 * @param name the declared name
 * @param identifier the native identifier scoped to its world/protocol
 * @param protocolVersion the protocol number, not assumed portable across versions
 */
public record PacketType(String name, int identifier, int protocolVersion) {

    /**
     * Requires a name and a native identifier in 0..1023; the protocol number scopes its interpretation.
     *
     * @param name the declared name
     * @param identifier the native identifier scoped to its world/protocol
     * @param protocolVersion the protocol number, not assumed portable across versions
     */
    public PacketType {
        Objects.requireNonNull(name);
        if (identifier < 0 || identifier > 1023) {
            throw new IllegalArgumentException("Invalid packet identifier");
        }
    }
}
