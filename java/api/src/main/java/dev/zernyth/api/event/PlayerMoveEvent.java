package dev.zernyth.api.event;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import dev.zernyth.api.player.Location;
import dev.zernyth.api.player.Player;

/**
 * Synchronous cancellable native movement event. Locations are detached; native cancellation remains
 * callback-scoped.
 */
public final class PlayerMoveEvent extends CancellableEvent {

    private final Location from, to;

    /**
     * Returns the detached movement origin.
     *
     * @return the detached movement origin
     */
    public Location getFrom() {
        return from;
    }

    /**
     * Returns the detached movement destination.
     *
     * @return the detached movement destination
     */
    public Location getTo() {
        return to;
    }

    /**
     * Retains detached from/to locations and callback-scoped cancellation accessors.
     *
     * @param player the associated session-checked player view
     * @param read the callback-scoped native cancellation reader
     * @param write the callback-scoped native cancellation writer
     * @param from the detached movement origin
     * @param to the detached movement destination
     */
    public PlayerMoveEvent(
            Player player, BooleanSupplier read, Consumer<Boolean> write, Location from, Location to) {
        super(player, read, write);
        this.from = from;
        this.to = to;
    }
}
