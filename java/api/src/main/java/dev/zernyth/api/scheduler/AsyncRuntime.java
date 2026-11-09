package dev.zernyth.api.scheduler;

/**
 * Submit/configure on the server thread. returnToServer, cancellation and immutable snapshots are
 * worker-safe. CPU uses Endstone workers; IO uses quota-limited virtual threads. Neither permits native
 * worker access.
 */
public interface AsyncRuntime {

    /**
     * Admits bounded work on the server thread without waiting for capacity; lifecycle/capacity failures
     * reject explicitly.
     *
     * @param kind the managed execution target
     * @param action the Java task callback
     * @param options the immutable task, profiler or control settings
     * @return the admitted task handle
     * @throws TaskRejectedException if admission is closed or a resource limit is exhausted
     * @throws IllegalStateException if invoked off the server thread or with an invalid native owner
     */
    TaskHandle submit(TaskKind kind, TaskAction action, TaskOptions options);

    /**
     * Admits immediate Java computation to bounded Endstone workers; no native gameplay access.
     *
     * @param action the Java task callback
     * @return the admitted CPU task handle
     */
    default TaskHandle submitCpu(TaskAction action) {
        return submit(TaskKind.CPU, action, TaskOptions.immediate());
    }

    /**
     * Admits immediate Java I/O to quota-limited virtual threads; no native gameplay access.
     *
     * @param action the Java task callback
     * @return the admitted I/O task handle
     */
    default TaskHandle submitIo(TaskAction action) {
        return submit(TaskKind.IO, action, TaskOptions.immediate());
    }

    /**
     * Queues work for the server-thread pulse without blocking. Worker-safe after server-thread
     * initialization.
     *
     * @param action the Java task callback
     * @return the admitted server-thread task handle
     * @throws TaskRejectedException if the runtime is not initialized, is closed, or has no capacity
     */
    TaskHandle returnToServer(Runnable action);

    /**
     * Requests cancellation on the server thread; running tasks remain visible until actual exit.
     */
    void cancelAll();

    /**
     * Configures own-plugin profiling or idle runtime limits on the server thread.
     *
     * @param limits the per-plugin limits; runtime must have no outstanding tasks
     * @throws IllegalStateException if the runtime is closed, tasks are outstanding, or the thread is invalid
     */
    void configure(RuntimeLimits limits);

    /**
     * Reads immutable per-plugin runtime limits.
     *
     * @return the immutable per-plugin limits
     */
    RuntimeLimits limits();

    /**
     * Reads detached current task/runtime state; concurrent fields are observational, not transactional.
     *
     * @return the detached current state
     */
    AsyncSnapshot snapshot();
}
