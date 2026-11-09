package dev.zernyth.api.network;

/**
 * Only capabilities returned by the manager are enabled.
 */
public enum NetworkCapability {
    /**
     * Read-only metadata observation.
     */
    PACKET_METADATA,
    /**
     * Lifetime-checked player-associated views.
     */
    PLAYER_CONNECTION_LIFETIME,
    /**
     * Decoded-field capability, unavailable in the current metadata-only adapter.
     */
    DECODED_FIELDS,
    /**
     * Sending capability, unavailable in the current adapter.
     */
    PACKET_SENDING,
    /**
     * Cancellation capability, unavailable in the current adapter.
     */
    PACKET_CANCELLATION,
    /**
     * Modification capability, unavailable in the current adapter.
     */
    PACKET_MODIFICATION,
    /**
     * Transport-level events, unavailable in the current adapter.
     */
    TRANSPORT_CONNECTION_EVENTS,
    /**
     * Async observation, unavailable because the current adapter has no such queue.
     */
    ASYNC_OBSERVATION
}
