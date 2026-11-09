package dev.zernyth.api.network;

/**
 * Synchronous server-thread callback. Never block on I/O; use copied values for deferred processing.
 */
@FunctionalInterface
public interface PacketListener {

    /**
     * Observes immutable metadata synchronously on the server thread. Do not block on I/O.
     *
     * @param packet the immutable metadata snapshot, retainable after callback return
     */
    void onPacket(PacketSnapshot packet);
}
