package dev.zernyth.api.network;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * No live player/packet references. Cancellation is the state observed at this listener's priority.
 *
 * @param playerId the player UUID when safely available
 * @param connectionId the player-associated ID when available
 * @param observedAt the observation instant
 * @param phase the native observation phase
 * @param subClientId the native subclient index, 0..3
 * @param alreadyCancelled the cancellation state already observed at this priority
 */
public record PacketContext(Optional<UUID> playerId, Optional<String> connectionId, Instant observedAt,
                            Phase phase, int subClientId, boolean alreadyCancelled) {

    /**
     * Exact native observation phase. Inbound observations precede native deserialization and do not expose
     * decoded fields.
     */
    public enum Phase {
        /**
         * Inbound observation before native deserialization; metadata only.
         */
        INBOUND_BEFORE_NATIVE_DESERIALIZATION,
        /**
         * Serialized outbound observation before batching; metadata
         * only.
         */
        OUTBOUND_SERIALIZED_BEFORE_BATCH
    }

    /**
     * Requires detached context values and a subclient index in 0..3; it does not retain a live packet or
     * player.
     *
     * @param playerId the player UUID when safely available
     * @param connectionId the player-associated ID when available
     * @param observedAt the observation instant
     * @param phase the native observation phase
     * @param subClientId the native subclient index, 0..3
     * @param alreadyCancelled the cancellation state already observed at this priority
     */
    public PacketContext {
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(connectionId);
        Objects.requireNonNull(observedAt);
        Objects.requireNonNull(phase);
        if (subClientId < 0 || subClientId > 3) {
            throw new IllegalArgumentException("Invalid subclient");
        }
    }
}
