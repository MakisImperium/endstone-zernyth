package dev.zernyth.api.scheduler;

/**
 * Detached managed task counts. Running includes cancellation-requested work until it actually exits; global
 * counts span managed plugin runtimes.
 *
 * @param generation the owning plugin generation
 * @param closed whether the runtime has closed admission
 * @param outstanding the owned nonterminal task count
 * @param queued the queued or dispatched-but-not-running task count
 * @param running running tasks, including cancellation-requested work
 * @param accepted the cumulative admission count
 * @param rejected the cumulative admission-rejection count
 * @param timeouts the diagnostic timeout count
 * @param globalOutstanding the global outstanding managed task count
 * @param globalCpuRunning the global CPU permit count
 * @param globalIoRunning the global I/O permit count
 */
public record AsyncSnapshot(long generation, boolean closed, int outstanding, int queued, int running,
                            long accepted, long rejected, long timeouts, int globalOutstanding,
                            int globalCpuRunning,
                            int globalIoRunning) {

}
