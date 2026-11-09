package dev.zernyth.api.scheduler;

/**
 * Managed lifecycle state. CANCEL_REQUESTED is nonterminal until running work really exits.
 */
public enum TaskStatus {
    /**
     * Admitted and waiting for a due tick and capacity.
     */
    QUEUED,
    /**
     * Dispatched but not yet executing.
     */
    DISPATCHED,
    /**
     * Callback execution has started.
     */
    RUNNING,
    /**
     * Running work has been asked to stop but has not exited.
     */
    CANCEL_REQUESTED,
    /**
     * Work completed successfully.
     */
    SUCCEEDED,
    /**
     * Callback or dispatch failed.
     */
    FAILED,
    /**
     * Cancellation completed; the operation is terminal.
     */
    CANCELLED
}
