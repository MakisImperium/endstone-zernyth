package dev.zernyth.api.scheduler;

/**
 * Submit/cancel on the server thread. Async Runnables must not access native API objects.
 */
public interface Scheduler {

    /**
     * Schedules a one-shot native server-thread callback.
     *
     * @param action the Java task callback
     * @return the native task handle
     */
    Task runTask(Runnable action);

    /**
     * Schedules a delayed native server-thread callback.
     *
     * @param action the Java task callback
     * @param delay the nonnegative delay in native ticks
     * @return the native task handle
     */
    Task runTaskLater(Runnable action, long delay);

    /**
     * Schedules a repeating native server-thread callback; period must be positive.
     *
     * @param action the Java task callback
     * @param delay the nonnegative delay in native ticks
     * @param period the positive repeat interval in native ticks
     * @return the native task handle
     */
    Task runTaskTimer(Runnable action, long delay, long period);

    /**
     * Schedules one-shot work on Endstone workers; no native gameplay access.
     *
     * @param action the Java task callback
     * @return the native task handle
     */
    Task runTaskAsync(Runnable action);

    /**
     * Schedules delayed work on Endstone workers; no native gameplay access.
     *
     * @param action the Java task callback
     * @param delay the nonnegative delay in native ticks
     * @return the native task handle
     */
    Task runTaskLaterAsync(Runnable action, long delay);

    /**
     * Schedules repeating work on Endstone workers; positive period, no native gameplay access.
     *
     * @param action the Java task callback
     * @param delay the nonnegative delay in native ticks
     * @param period the positive repeat interval in native ticks
     * @return the native task handle
     */
    Task runTaskTimerAsync(Runnable action, long delay, long period);

    /**
     * Requests cancellation of owned native and managed tasks; running code is not force-stopped.
     */
    void cancelTasks();
}
