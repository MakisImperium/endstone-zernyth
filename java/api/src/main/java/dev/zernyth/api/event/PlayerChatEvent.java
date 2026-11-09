package dev.zernyth.api.event;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.zernyth.api.player.Player;

/**
 * Synchronous cancellable native chat event. Message and cancellation access expire when this listener
 * returns.
 */
public final class PlayerChatEvent extends CancellableEvent {

    private final Supplier<String> readMessage;
    private final Consumer<String> writeMessage;

    /**
     * Reads the native chat message within this callback.
     *
     * @return the current native chat message
     */
    public String getMessage() {
        return readMessage.get();
    }

    /**
     * Updates the native chat message within this callback.
     *
     * @param value the replacement value; null is permitted only where the enclosing contract specifies
     */
    public void setMessage(String value) {
        writeMessage.accept(Objects.requireNonNull(value));
    }

    /**
     * Binds chat-message and cancellation accessors to the current native invocation.
     *
     * @param player the associated session-checked player view
     * @param read the callback-scoped native cancellation reader
     * @param write the callback-scoped native cancellation writer
     * @param readMessage the callback-scoped chat-message reader
     * @param writeMessage the callback-scoped chat-message writer
     */
    public PlayerChatEvent(Player player, BooleanSupplier read, Consumer<Boolean> write,
            Supplier<String> readMessage, Consumer<String> writeMessage) {
        super(player, read, write);
        this.readMessage = readMessage;
        this.writeMessage = writeMessage;
    }
}
