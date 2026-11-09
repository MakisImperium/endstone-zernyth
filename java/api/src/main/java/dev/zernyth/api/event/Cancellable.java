package dev.zernyth.api.event;

/**
 * Synchronous callback-scoped native cancellation. Updates are returned directly to Endstone.
 */
public interface Cancellable {

    /**
     * Reads native cancellation during the current callback.
     *
     * @return the cancellation state
     */
    boolean isCancelled();

    /**
     * Synchronously updates native cancellation during the current callback.
     *
     * @param cancelled the requested cancellation state
     */
    void setCancelled(boolean cancelled);
}
