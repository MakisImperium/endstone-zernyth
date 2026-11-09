package dev.zernyth.api.performance;

/**
 * Own-plugin diagnostics, queried/configured on the server thread. Returned records are worker-safe.
 */
public interface PerformanceManager {

    /**
     * Configures own-plugin profiling or idle runtime limits on the server thread.
     *
     * @param options the immutable task, profiler or control settings
     */
    void configure(PerformanceOptions options);

    /**
     * Reads own-generation cumulative callback, task and native bridge measurements.
     *
     * @return the detached own-generation measurements
     */
    PluginPerformanceSnapshot getPluginSnapshot();

    /**
     * Reads process-wide JVM/GC and weak-loader observations, not per-plugin heap attribution.
     *
     * @return the process-wide memory/GC observation
     */
    RuntimeMemorySnapshot getRuntimeMemorySnapshot();
}
