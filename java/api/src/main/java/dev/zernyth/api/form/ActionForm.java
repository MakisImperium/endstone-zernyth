package dev.zernyth.api.form;

import java.util.List;
import java.util.Objects;

/**
 * Immutable native action-form description with one to 256 copied buttons. Sending requires an online player
 * and server thread.
 *
 * @param title the form title
 * @param content the form body text
 * @param buttons one to 256 button descriptions, copied
 */
public record ActionForm(String title, String content, List<Button> buttons) implements Form {

    /**
     * Copies the buttons and rejects missing title/content or a button count outside 1..256.
     *
     * @param title the form title
     * @param content the form body text
     * @param buttons one to 256 button descriptions, copied
     */
    public ActionForm {
        Objects.requireNonNull(title);
        Objects.requireNonNull(content);
        buttons = List.copyOf(buttons);
        if (buttons.isEmpty() || buttons.size() > 256) {
            throw new IllegalArgumentException("Expected 1..256 buttons");
        }
    }

    /**
     * Action-form button with an optional image reference. A null image means no image.
     *
     * @param text the literal text
     * @param image the optional image reference; null means no image
     */
    public record Button(String text, String image) {

        /**
         * Captures a non-null button label and optional image; null means no image.
         *
         * @param text the literal text
         * @param image the optional image reference; null means no image
         */
        public Button {
            Objects.requireNonNull(text);
        }

        /**
         * Captures a non-null button label and optional image; null means no image.
         *
         * @param text the literal text
         */
        public Button(String text) {
            this(text, null);
        }
    }
}
