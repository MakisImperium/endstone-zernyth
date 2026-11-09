package dev.zernyth.api.scheduler;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable tick timing and timeout-diagnostic settings. Nonnegative delay/period; zero period means
 * one-shot. Positive warnAfter is at most one day and never forcibly terminates work.
 *
 * @param delayTicks the nonnegative initial delay in ticks
 * @param periodTicks the nonnegative period; zero means one-shot
 * @param warnAfter the positive diagnostic timeout, at most one day
 */
public record TaskOptions(long delayTicks, long periodTicks, Duration warnAfter) {

    /**
     * Requires nonnegative tick timing and a positive diagnostic threshold of at most one day.
     *
     * @param delayTicks the nonnegative initial delay in ticks
     * @param periodTicks the nonnegative period; zero means one-shot
     * @param warnAfter the positive diagnostic timeout, at most one day
     */
    public TaskOptions {
        Objects.requireNonNull(warnAfter);
        if (delayTicks < 0 || periodTicks < 0 || warnAfter.isNegative() || warnAfter.isZero()
                || warnAfter.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("Invalid task timing");
        }
    }

    /**
     * Creates one-shot timing with no delay and a 30-second diagnostic threshold.
     *
     * @return the one-shot immediate options
     */
    public static TaskOptions immediate() {
        return new TaskOptions(0, 0, Duration.ofSeconds(30));
    }
}
