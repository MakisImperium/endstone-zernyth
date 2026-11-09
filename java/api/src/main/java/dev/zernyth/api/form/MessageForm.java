package dev.zernyth.api.form;

import java.util.Objects;

/**
 * Immutable native two-button message-form description. Construction is worker-safe; sending is not.
 *
 * @param title the form title
 * @param content the form body text
 * @param firstButton the first action label
 * @param secondButton the second action label
 */
public record MessageForm(String title, String content, String firstButton, String secondButton)
        implements Form {

    /**
     * Requires non-null title, content and both button labels before the form is sent.
     *
     * @param title the form title
     * @param content the form body text
     * @param firstButton the first action label
     * @param secondButton the second action label
     */
    public MessageForm {
        Objects.requireNonNull(title);
        Objects.requireNonNull(content);
        Objects.requireNonNull(firstButton);
        Objects.requireNonNull(secondButton);
    }
}
