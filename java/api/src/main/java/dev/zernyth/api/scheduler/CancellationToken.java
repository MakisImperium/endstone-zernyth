package dev.zernyth.api.scheduler;

import java.util.concurrent.CancellationException;

/**
 * Worker-safe cooperative cancellation signal. A cancellation request is not completion and does not forcibly
 * abort native work.
 */
public interface CancellationToken {

    /**
     * Reads the cooperative cancellation signal without blocking.
     *
     * @return whether cancellation was requested
     */
    boolean isCancellationRequested();

    /**
     * Throws CancellationException when cancellation was requested.
     */
    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new CancellationException("Task cancellation requested");
        }
    }
}
