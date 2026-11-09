package dev.zernyth.api.scheduler;

/**
 * Server-thread cancellation; cancelling does not interrupt an already running callback.
 */
public interface Task {

    /**
     * Requests cancellation under this handle's threading contract; it does not prove running work has ended.
     */
    void cancel();

    /**
     * Reads cancellation state. Task cancellation does not prove running work has ended.
     *
     * @return the cancellation state
     */
    boolean isCancelled();
}
