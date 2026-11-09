package dev.zernyth.api.service;

/**
 * Native service provider ordering; higher-priority providers are preferred by lookup.
 */
public enum ServicePriority {
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
    HIGHEST
}
