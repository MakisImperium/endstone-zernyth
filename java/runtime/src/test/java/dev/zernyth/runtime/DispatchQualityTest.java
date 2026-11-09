package dev.zernyth.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import dev.zernyth.api.event.Event;
import dev.zernyth.api.performance.PerformanceOptions;
import dev.zernyth.api.player.Player;

/**
 * Bounded regression checks for reflective invocation contracts and concurrent profiling.
 */
final class DispatchQualityTest {

    private static final class Probe extends Event {

        Probe() {
            super((Player) Proxy.newProxyInstance(
                    DispatchQualityTest.class.getClassLoader(),
                    new Class<?>[]{Player.class}, (proxy, method, arguments) -> {
                        throw new UnsupportedOperationException("Unused player stub");
                    }));
        }
    }

    public static final class Listener {

        int calls;

        public void accept(Probe event) {
            calls++;
        }

        public void fail(Probe event) {
            throw new IllegalStateException("expected listener failure");
        }
    }

    static void run() throws Exception {
        Listener listener = new Listener();
        var invocation = Listener.class.getMethod("accept", Probe.class);
        invocation.invoke(listener, new Probe());
        if (listener.calls != 1) {
            throw new AssertionError("Reflective invocation lost event");
        }
        try {
            Listener.class.getMethod("fail", Probe.class).invoke(listener, new Probe());
            throw new AssertionError("Listener failure was swallowed");
        } catch (InvocationTargetException expected) {
            if (!(expected.getCause() instanceof IllegalStateException)) {
                throw new AssertionError(expected);
            }
        }

        var warnings = new AtomicInteger();
        var profile = new Profile(message -> warnings.incrementAndGet());
        profile.options = new PerformanceOptions(true, Duration.ofDays(1), 100);
        var start = new CountDownLatch(1);
        Thread[] workers = new Thread[4];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = Thread.ofPlatform().daemon().unstarted(() -> {
                try {
                    start.await();
                } catch (InterruptedException error) {
                    throw new AssertionError(error);
                }
                for (int n = 0; n < 1000; n++) {
                    profile.measure("event", "concurrent", () -> null);
                }
            });
            workers[i].start();
        }
        start.countDown();
        for (Thread worker : workers) {
            worker.join(5000);
            if (worker.isAlive()) {
                throw new AssertionError("Profiler test exceeded deadline");
            }
        }
        var row = profile.snapshot().getFirst();
        if (row.calls() != 4000 || row.exceptions() != 0 || row.maxWallNanos() <= 0
                || row.maxWallNanos() > row.wallNanos() || warnings.get() != 0) {
            throw new AssertionError("Concurrent profiler counters inconsistent: " + row);
        }
        try {
            profile.measure("event", "failure", () -> {
                throw new IllegalArgumentException("expected");
            });
            throw new AssertionError("Profiler swallowed failure");
        } catch (IllegalArgumentException expected) {
        }
        if (profile.snapshot().getFirst().exceptions() != 1) {
            throw new AssertionError("Missing error counter");
        }

        profile.options = new PerformanceOptions(true, Duration.ofDays(1), 100);
        profile.measure("options", "entry snapshot", () -> {
            profile.options = new PerformanceOptions(true, Duration.ofNanos(1), 100);
            return null;
        });
        var optionsRow = profile.snapshot()
                .stream()
                .filter(value -> value.kind().equals("options"))
                .findFirst()
                .orElseThrow();
        if (optionsRow.slowCalls() != 0) {
            throw new AssertionError("Callback did not retain its entry threshold");
        }

        var disabled = new Profile(message -> {
            throw new AssertionError("Disabled profiler logged");
        });
        if (disabled.measure("event", "disabled", () -> 17) != 17 || !disabled.snapshot().isEmpty()) {
            throw new AssertionError("Disabled profiling changed callback results or recorded calls");
        }
        System.out.println("Dispatch invocation and bounded concurrent profiler checks passed.");
    }
}
