package dev.zernyth.api.network.exception;

import java.io.Serial;

/**
 * No compatible native metadata adapter/filter exists for the current protocol.
 */
public final class UnsupportedPacketException extends UnsupportedOperationException {

    // Preserve the UID computed by the pre-refactoring API artifact.
    @Serial
    private static final long serialVersionUID = 1383696426450048333L;

    /**
     * Reports an unsupported adapter or capability without exposing packet/session internals.
     *
     * @param message the message text or immutable component
     */
    public UnsupportedPacketException(String message) {
        super(message);
    }
}
