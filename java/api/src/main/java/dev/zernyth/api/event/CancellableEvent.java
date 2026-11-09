package dev.zernyth.api.event;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import dev.zernyth.api.player.Player;

/**
 * Callback-scoped native event backed by cancellation accessors. Never retain it for later cancellation or
 * access it from workers.
 */
public abstract class CancellableEvent extends Event implements Cancellable {

    private final BooleanSupplier read;
    private final Consumer<Boolean> write;

    /**
     * Binds cancellation accessors to this invocation. The runtime must supply valid accessors and enforce
     * their callback lifetime.
     *
     * @param player the associated session-checked player view
     * @param read the callback-scoped native cancellation reader
     * @param write the callback-scoped native cancellation writer
     */
    protected CancellableEvent(Player player, BooleanSupplier read, Consumer<Boolean> write) {
        super(player);
        this.read = read;
        this.write = write;
    }

    /**
     * Reads native cancellation during the current callback.
     *
     * @return the cancellation state
     */
    public final boolean isCancelled() {
        return read.getAsBoolean();
    }

    /**
     * Synchronously updates native cancellation during the current callback.
     *
     * @param value the cancellation state to write synchronously
     */
    public final void setCancelled(boolean value) {
        write.accept(value);
    }
}
