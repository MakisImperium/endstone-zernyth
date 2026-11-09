package dev.zernyth.api.performance;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable profiler settings. Threshold must be positive and at most one day; warning frequency must be
 * positive. Disabled by default.
 *
 * @param enabled whether timing diagnostics should be collected
 * @param slowCallbackThreshold the positive slow threshold, at most one day
 * @param warnEvery the positive aggregated warning frequency
 */
public record PerformanceOptions(boolean enabled, Duration slowCallbackThreshold, int warnEvery) {

    /**
     * Rejects a nonpositive or greater-than-one-day threshold and a nonpositive warning frequency.
     *
     * @param enabled whether timing diagnostics should be collected
     * @param slowCallbackThreshold the positive slow threshold, at most one day
     * @param warnEvery the positive aggregated warning frequency
     */
    public PerformanceOptions {
        Objects.requireNonNull(slowCallbackThreshold);
        if (slowCallbackThreshold.isNegative() || slowCallbackThreshold.isZero()
                || slowCallbackThreshold.compareTo(Duration.ofDays(1)) > 0 || warnEvery < 1) {
            throw new IllegalArgumentException("Invalid profiler options");
        }
    }

    /**
     * Creates disabled profiling options with a 10 ms slow threshold and warning frequency 100.
     *
     * @return the disabled profiler options
     */
    public static PerformanceOptions disabled() {
        return new PerformanceOptions(false, Duration.ofMillis(10), 100);
    }
}
