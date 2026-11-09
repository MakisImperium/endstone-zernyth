package dev.zernyth.api.form;

import java.util.List;

/**
 * Action/Message responses contain a zero-based Integer selection; Modal values follow control order.
 *
 * @param status the observed task/response lifecycle state
 * @param values the detached values; collection inputs are copied where specified
 */
public record FormResponse(Status status, List<Object> values) {

    /**
     * Copies response values so later mutation of the supplied list cannot alter the response.
     *
     * @param status the observed task/response lifecycle state
     * @param values the detached values; collection inputs are copied where specified
     */
    public FormResponse {
        values = List.copyOf(values);
    }

    /**
     * Terminal form-response classification; non-submitted responses must not be interpreted as selections.
     */
    public enum Status {
        /**
         * A validated client response was received.
         */
        SUBMITTED,
        /**
         * The player association or form UI has closed, according to the enclosing type.
         */
        CLOSED,
        /**
         * The player disconnected before a response.
         */
        DISCONNECTED,
        /**
         * The owner was disabled before a response.
         */
        PLUGIN_DISABLED,
        /**
         * Client response failed validation.
         */
        INVALID_RESPONSE,
        /**
         * Cancellation completed; the operation is terminal.
         */
        CANCELLED
    }
}
