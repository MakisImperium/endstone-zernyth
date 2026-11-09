package dev.zernyth.api.scheduler;

/**
 * Wall-clock durations, never CPU usage. Queue wait includes configured delay and native dispatch wait.
 *
 * @param id the task identifier
 * @param generation the owning plugin generation
 * @param kind the execution target or measured callback category
 * @param status the observed task/response lifecycle state
 * @param runs the completed callback invocation count
 * @param queueWaitNanos the cumulative wall-clock queue/delay wait
 * @param wallNanos the accumulated wall-clock nanoseconds, not CPU usage
 * @param cancellationRequested whether cooperative cancellation was requested
 * @param timedOut whether the diagnostic timeout was observed
 * @param failureType the error class name, empty when absent
 */
public record TaskSnapshot(long id, long generation, TaskKind kind, TaskStatus status, long runs,
                           long queueWaitNanos, long wallNanos, boolean cancellationRequested,
                           boolean timedOut,
                           String failureType) {

    /**
     * Checks completed success, failure or cancellation; CANCEL_REQUESTED is nonterminal.
     *
     * @return whether the recorded task status is terminal
     */
    public boolean terminal() {
        return status == TaskStatus.SUCCEEDED || status == TaskStatus.FAILED
                || status == TaskStatus.CANCELLED;
    }
}
