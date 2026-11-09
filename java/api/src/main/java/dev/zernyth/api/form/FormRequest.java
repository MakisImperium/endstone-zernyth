package dev.zernyth.api.form;

import java.util.concurrent.CompletionStage;

/**
 * Never block/join on a response. Cancel on the server thread; it detaches only this plugin's callback.
 */
public interface FormRequest {

    /**
     * Returns the nonblocking form response stage; never wait/join on the server thread.
     *
     * @return the response completion stage
     */
    CompletionStage<FormResponse> response();

    /**
     * Requests cancellation under this handle's threading contract; it does not prove running work has ended.
     */
    void cancel();
}
