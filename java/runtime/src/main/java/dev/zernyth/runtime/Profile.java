package dev.zernyth.runtime;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

import dev.zernyth.api.performance.CallbackPerformance;
import dev.zernyth.api.performance.PerformanceOptions;

/**
 * Per-owner callback wall-clock counters. Workers update concurrent accumulators; snapshots are
 * observations rather than an atomic transaction. Only bounded internal category names are keys.
 * No plugin classes, methods or classloaders are stored in a JVM-wide cache.
 */
final class Profile {

    interface Work<T> {

        T run() throws Exception;
    }

    private static final class Counter {

        final LongAdder calls = new LongAdder();
        final LongAdder errors = new LongAdder();
        final LongAdder nanos = new LongAdder();
        final LongAdder slow = new LongAdder();
        final AtomicLong max = new AtomicLong();
    }

    volatile PerformanceOptions options = PerformanceOptions.disabled();
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> warnings = new ConcurrentHashMap<>();
    private final Consumer<String> logger;

    Profile(Consumer<String> logger) {
        this.logger = logger;
    }

    <T> T measure(String kind, String label, Work<T> work) {
        PerformanceOptions currentOptions = options;
        boolean enabled = currentOptions.enabled();
        long start = enabled ? System.nanoTime() : 0;
        Counter counter = enabled ? counters.computeIfAbsent(kind, k -> new Counter()) : null;
        if (counter != null) {
            counter.calls.increment();
        }
        try {
            return work.run();
        } catch (Throwable error) {
            if (counter != null) {
                counter.errors.increment();
            }
            if (error instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (error instanceof Error fatal) {
                throw fatal;
            }
            throw new CompletionException(error);
        } finally {
            if (counter != null) {
                long elapsed = System.nanoTime() - start;
                counter.nanos.add(elapsed);
                // Most calls do not set a new maximum. Avoid a contended write in that common case.
                if (elapsed > counter.max.get()) {
                    counter.max.accumulateAndGet(elapsed, Math::max);
                }
                if (elapsed >= currentOptions.slowCallbackThreshold().toNanos()) {
                    counter.slow.increment();
                    warning("slow-" + kind, label);
                }
            }
        }
    }

    void warning(String kind, String label) {
        // Aggregate by bounded category, never by unbounded user labels.
        long count = warnings.computeIfAbsent(kind, k -> new AtomicLong()).incrementAndGet();
        if (count == 1 || count % options.warnEvery() == 0) {
            try {
                logger.accept(kind + " [" + label + "] on "
                        + (Thread.currentThread().isVirtual() ? "virtual" : "platform") + " thread; " + count
                        + " occurrences");
            } catch (Throwable ignored) { /* Disabled owners must not turn diagnostics into task failures. */
            }
        }
    }

    List<CallbackPerformance> snapshot() {
        return counters.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    var counter = entry.getValue();
                    return new CallbackPerformance(entry.getKey(), counter.calls.sum(), counter.errors.sum(),
                            counter.nanos.sum(), counter.max.get(), counter.slow.sum());
                })
                .toList();
    }
}
