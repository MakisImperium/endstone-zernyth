package dev.zernyth.api.scheduler;

/**
 * Per-plugin bounds: outstanding 1..4096, CPU 1..16, I/O 1..128 and server callbacks per tick 1..256. Global
 * quotas may impose lower effective capacity.
 *
 * @param maxOutstanding the per-plugin outstanding-task limit, 1..4096
 * @param cpuParallelism the per-plugin CPU concurrency limit, 1..16
 * @param ioParallelism the per-plugin I/O concurrency limit, 1..128
 * @param maxServerCallbacksPerTick the server callback count limit per pulse, 1..256
 */
public record RuntimeLimits(
        int maxOutstanding, int cpuParallelism, int ioParallelism, int maxServerCallbacksPerTick) {

    /**
     * Checks per-plugin bounds before the limits can be installed. Construction performs no native calls.
     *
     * @param maxOutstanding the per-plugin outstanding-task limit, 1..4096
     * @param cpuParallelism the per-plugin CPU concurrency limit, 1..16
     * @param ioParallelism the per-plugin I/O concurrency limit, 1..128
     * @param maxServerCallbacksPerTick the server callback count limit per pulse, 1..256
     */
    public RuntimeLimits {
        if (maxOutstanding < 1 || maxOutstanding > 4096 || cpuParallelism < 1 || cpuParallelism > 16
                || ioParallelism < 1 || ioParallelism > 128 || maxServerCallbacksPerTick < 1
                || maxServerCallbacksPerTick > 256) {
            throw new IllegalArgumentException("Invalid async runtime limits");
        }
    }

    /**
     * Creates limits of 256 outstanding, 1 CPU, 4 I/O and 32 server callbacks per tick.
     *
     * @return the default runtime limits
     */
    public static RuntimeLimits defaults() {
        return new RuntimeLimits(256, 1, 4, 32);
    }
}
