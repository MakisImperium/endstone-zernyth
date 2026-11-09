package dev.zernyth.api.scheduler;

import java.io.Serial;
import java.util.concurrent.RejectedExecutionException;

/**
 * Admission failure caused by runtime lifecycle, initialization or bounded capacity. Tasks are rejected
 * explicitly, not silently discarded.
 */
public final class TaskRejectedException extends RejectedExecutionException {

    // Preserve the UID computed by the pre-refactoring API artifact.
    @Serial
    private static final long serialVersionUID = 8456679845230733104L;

    /**
     * Reports why bounded task admission failed without implying that work was executed.
     *
     * @param message the message text or immutable component
     */
    public TaskRejectedException(String message) {
        super(message);
    }
}
