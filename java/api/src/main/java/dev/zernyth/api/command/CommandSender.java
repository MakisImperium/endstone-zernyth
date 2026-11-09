package dev.zernyth.api.command;

import dev.zernyth.api.text.Component;
import dev.zernyth.api.text.Component.Text;

/**
 * Native access requires the server thread. Command callback senders expire on return.
 */
public interface CommandSender {

    /**
     * Returns this object's declared or native name.
     *
     * @return the name
     */
    String getName();

    /**
     * Delivers literal text through the native sender on its designated server thread.
     *
     * @param message the literal message, non-null
     */
    void sendMessage(String message);

    /**
     * Delivers a text component using this sender's messaging implementation.
     * The compatibility default adapts literal text to the String overload; translation
     * components require an implementation that supports native translation delivery.
     *
     * @param message the message text or immutable component
     * @throws UnsupportedOperationException if this implementation cannot deliver the supplied component
     * @implSpec Literal components delegate to sendMessage(String). Zernyth overrides this method
     * for native player and command senders to support its implemented translation path.
     */
    default void sendMessage(Component message) {
        if (message instanceof Text text) {
            sendMessage(text.content());
        } else {
            throw new UnsupportedOperationException("Native translations unavailable on this sender");
        }
    }

    /**
     * Checks the native permission system.
     *
     * @param permission the permission node; an empty command permission adds no extra node
     * @return whether the permission is granted
     */
    boolean hasPermission(String permission);
}
