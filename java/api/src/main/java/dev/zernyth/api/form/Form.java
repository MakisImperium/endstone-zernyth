package dev.zernyth.api.form;

/**
 * Immutable description. Sending/response completion uses the native server thread.
 */
public sealed interface Form permits ActionForm, MessageForm, ModalForm {

    /**
     * Returns the client-visible form title.
     *
     * @return the form title
     */
    String title();
}
