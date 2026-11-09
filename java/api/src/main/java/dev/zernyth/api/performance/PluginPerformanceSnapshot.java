package dev.zernyth.api.performance;

import java.util.List;

import dev.zernyth.api.scheduler.AsyncSnapshot;

/**
 * Detached own-generation measurements. Callback rows are copied; native bridge durations are wall-clock
 * values, not pure JNI transition time or CPU usage.
 *
 * @param generation the owning plugin generation
 * @param callbacks the copied callback measurements
 * @param async the detached managed runtime observation
 * @param nativeBridgeCalls the measured native bridge call count
 * @param nativeBridgeWallNanos the cumulative measured bridge wall-clock nanoseconds
 * @param maxNativeBridgeWallNanos the maximum measured bridge wall-clock nanoseconds
 * @param nativeBridgeExceptions the measured native bridge error count
 */
public record PluginPerformanceSnapshot(long generation, List<CallbackPerformance> callbacks,
                                        AsyncSnapshot async, long nativeBridgeCalls,
                                        long nativeBridgeWallNanos,
                                        long maxNativeBridgeWallNanos, long nativeBridgeExceptions) {

    /**
     * Copies callback rows so later mutation of the supplied list cannot change the snapshot.
     *
     * @param generation the owning plugin generation
     * @param callbacks the copied callback measurements
     * @param async the detached managed runtime observation
     * @param nativeBridgeCalls the measured native bridge call count
     * @param nativeBridgeWallNanos the cumulative measured bridge wall-clock nanoseconds
     * @param maxNativeBridgeWallNanos the maximum measured bridge wall-clock nanoseconds
     * @param nativeBridgeExceptions the measured native bridge error count
     */
    public PluginPerformanceSnapshot {
        callbacks = List.copyOf(callbacks);
    }
}
