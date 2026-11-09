package dev.zernyth.api.event;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import dev.zernyth.api.player.Player;

/**
 * Synchronous cancellable native interaction event with a captured action value.
 */
public final class PlayerInteractEvent extends CancellableEvent {

    /**
     * Supported native interaction actions.
     */
    public enum Action {
        /**
         * Left click on a block.
         */
        LEFT_CLICK_BLOCK,
        /**
         * Right click on a block.
         */
        RIGHT_CLICK_BLOCK,
        /**
         * Left click without a block target.
         */
        LEFT_CLICK_AIR,
        /**
         * Right click without a block target.
         */
        RIGHT_CLICK_AIR
    }

    private final Action action;

    /**
     * Returns the captured native interaction action.
     *
     * @return the captured interaction action
     */
    public Action getAction() {
        return action;
    }

    /**
     * Associates the captured interaction action with this invocation's cancellation accessors.
     *
     * @param player the associated session-checked player view
     * @param read the callback-scoped native cancellation reader
     * @param write the callback-scoped native cancellation writer
     * @param action the task callback or captured interaction action
     */
    public PlayerInteractEvent(Player player, BooleanSupplier read, Consumer<Boolean> write, Action action) {
        super(player, read, write);
        this.action = action;
    }
}
