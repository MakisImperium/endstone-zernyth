package dev.zernyth.api.scheduler;

import java.util.concurrent.CompletionStage;

/**
 * Java-only cancellation/status; safe from workers and after disable. Cancellation is cooperative.
 */
public interface TaskHandle extends CancellationToken {

    /**
     * Requests cancellation under this handle's threading contract; it does not prove running work has ended.
     */
    void cancel();

    /**
     * Reads detached current task/runtime state; concurrent fields are observational, not transactional.
     *
     * @return the detached current state
     */
    TaskSnapshot snapshot();

    /**
     * Returns a read-only stage completed after actual terminal state. Never wait on the server thread.
     *
     * @return a read-only stage for actual terminal completion
     */
    CompletionStage<TaskSnapshot> completion();
}
