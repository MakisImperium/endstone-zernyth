package dev.zernyth.api.network;

import java.util.Set;

/**
 * Empty types means all IDs; directions must be nonempty. Filtering occurs before JNI.
 *
 * @param types the copied explicit packet filter; empty means all IDs
 * @param directions the nonempty copied direction filter
 */
public record PacketFilter(Set<PacketType> types, Set<PacketDirection> directions) {

    /**
     * Copies both sets, rejecting more than 128 explicit types or an empty direction set.
     *
     * @param types the copied explicit packet filter; empty means all IDs
     * @param directions the nonempty copied direction filter
     */
    public PacketFilter {
        types = Set.copyOf(types);
        directions = Set.copyOf(directions);
        if (types.size() > 128 || directions.isEmpty()) {
            throw new IllegalArgumentException("Invalid packet filter");
        }
    }

    /**
     * Creates an immutable all-IDs, both-directions filter.
     *
     * @return an all-IDs, both-directions filter
     */
    public static PacketFilter all() {
        return new PacketFilter(Set.of(), Set.of(PacketDirection.values()));
    }
}
