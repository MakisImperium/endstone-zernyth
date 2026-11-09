package dev.zernyth.runtime;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import dev.zernyth.api.event.Event;
import dev.zernyth.api.player.Player;

/**
 * Isolates the two invocation mechanisms, not native event dispatch or BDS throughput.
 */
public final class EventInvocationBenchmark {

    public static final class Probe extends Event {

        Probe(Player player) {
            super(player);
        }
    }

    public static final class Listener {

        public void accept(Probe event) {
            BenchmarkHarness.sink++;
        }
    }

    public static void main(String[] args) throws Exception {
        var player = (Player) Proxy.newProxyInstance(EventInvocationBenchmark.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, values) -> {
                    throw new UnsupportedOperationException("Unused player stub");
                });
        var event = new Probe(player);
        var listener = new Listener();
        Method method = Listener.class.getMethod("accept", Probe.class);
        var bound = MethodHandles.lookup().unreflect(method).bindTo(listener).asType(
                MethodType.methodType(void.class, Event.class));
        Runnable reflection = () -> {
            try {
                method.invoke(listener, event);
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        };
        Runnable invocation = () -> {
            try {
                // Rejected production candidate, kept here solely for reproducible comparison.
                bound.invokeExact((Event) event);
            } catch (Throwable error) {
                throw new AssertionError(error);
            }
        };
        // Alternate order between processes to expose simple order/JIT bias.
        if (Boolean.getBoolean("zernyth.benchmark.boundFirst")) {
            BenchmarkHarness.measure("bound-event-invocation", invocation, 10000);
            BenchmarkHarness.measure("reflective-event-invocation", reflection, 10000);
        } else {
            BenchmarkHarness.measure("reflective-event-invocation", reflection, 10000);
            BenchmarkHarness.measure("bound-event-invocation", invocation, 10000);
        }
        BenchmarkHarness.finish();
    }
}
