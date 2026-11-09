package dev.zernyth.api.event;

/**
 * Native event priority ordering. Priority never extends the callback lifetime or overrides thread
 * restrictions.
 */
public enum EventPriority {
    /**
     * Earliest native priority.
     */
    LOWEST,
    /**
     * Low native priority.
     */
    LOW,
    /**
     * Default native priority.
     */
    NORMAL,
    /**
     * High native priority.
     */
    HIGH,
    /**
     * Highest regular native priority.
     */
    HIGHEST,
    /**
     * Native monitor priority; ordinary thread/lifetime rules still apply.
     */
    MONITOR
}
