package dev.zernyth.api.event;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import dev.zernyth.api.player.Location;
import dev.zernyth.api.player.Player;

/**
 * Synchronous cancellable native block-place event with a detached block location.
 */
public final class BlockPlaceEvent extends CancellableEvent {

    private final Location blockLocation;

    /**
     * Returns the detached block location.
     *
     * @return the detached block location
     */
    public Location getBlockLocation() {
        return blockLocation;
    }

    /**
     * Associates the detached block location with this invocation's cancellation accessors.
     *
     * @param player the associated session-checked player view
     * @param read the callback-scoped native cancellation reader
     * @param write the callback-scoped native cancellation writer
     * @param blockLocation the detached block location
     */
    public BlockPlaceEvent(
            Player player, BooleanSupplier read, Consumer<Boolean> write, Location blockLocation) {
        super(player, read, write);
        this.blockLocation = blockLocation;
    }
}
