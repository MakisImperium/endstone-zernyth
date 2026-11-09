package dev.zernyth.api.scheduler;

/**
 * Managed execution target. Worker targets permit Java work only, not native gameplay access.
 */
public enum TaskKind {
    /**
     * Java computation on bounded Endstone workers; no native gameplay access.
     */
    CPU,
    /**
     * Java I/O on quota-limited virtual threads; no native gameplay access.
     */
    IO,
    /**
     * Work dispatched by the existing server-thread pulse.
     */
    SERVER
}
