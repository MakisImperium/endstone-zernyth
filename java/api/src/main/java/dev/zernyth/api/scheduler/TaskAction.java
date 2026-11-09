package dev.zernyth.api.scheduler;

/**
 * Managed task callback receiving cooperative cancellation. Execution follows TaskKind and does not relax
 * native gameplay thread restrictions.
 */
@FunctionalInterface
public interface TaskAction {

    /**
     * Executes Java work using the enclosing callback thread and exception contract.
     *
     * @param cancellation the cooperative cancellation token
     * @throws Exception if the task fails; the runtime records the failure rather than rethrowing it on the
     * server thread
     */
    void run(CancellationToken cancellation) throws Exception;
}
