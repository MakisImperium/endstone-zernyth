package dev.zernyth.api.performance;

/**
 * Cumulative wall-clock measurements while profiling was enabled.
 *
 * @param kind the execution target or measured callback category
 * @param calls the measured callback count
 * @param exceptions the measured callback exception count
 * @param wallNanos the accumulated wall-clock nanoseconds, not CPU usage
 * @param maxWallNanos the maximum measured callback wall-clock nanoseconds
 * @param slowCalls the callback count exceeding the configured threshold
 */
public record CallbackPerformance(
        String kind, long calls, long exceptions, long wallNanos, long maxWallNanos, long slowCalls) {

}
