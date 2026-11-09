package dev.zernyth.api.event;

/**
 * Server-thread only. Idempotent while the plugin is live.
 */
public interface Registration extends AutoCloseable {

    /**
     * Releases the registration/state under its server-thread and owner lifetime contract.
     */
    @Override
    void close();
}
