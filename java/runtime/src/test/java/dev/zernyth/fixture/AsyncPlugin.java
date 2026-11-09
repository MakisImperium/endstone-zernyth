package dev.zernyth.fixture;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import dev.zernyth.api.performance.PerformanceOptions;
import dev.zernyth.api.plugin.JavaPlugin;
import dev.zernyth.api.text.Component;

/**
 * Native-mock fixture for task ownership, virtual threads and rejected off-thread gameplay access.
 */
public final class AsyncPlugin extends JavaPlugin {

    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger serviceRejected = new AtomicInteger();
    private volatile boolean wrongThreadRejected, virtual;

    @Override
    public void onEnable() {
        getServer().getPerformanceManager().configure(
                new PerformanceOptions(true, Duration.ofMillis(100), 100));
        try {
            var translation = Class.forName("dev.zernyth.runtime.Native")
                    .getDeclaredMethod("translation", long.class, long.class, boolean.class,
                            String.class, String[].class);
            if (!translation.trySetAccessible()) {
                throw new AssertionError("Test bridge inaccessible");
            }
            long owner = getServer().getPerformanceManager().getPluginSnapshot().generation();
            Object[][] invalid = {{owner, Long.MAX_VALUE, false, "key", new String[0]},
                    {owner, 0L, true, "key", null}, {-1L, 0L, false, "key", new String[0]}};
            for (Object[] args : invalid) {
                try {
                    translation.invoke(null, args);
                    throw new AssertionError("Invalid JNI input accepted");
                } catch (InvocationTargetException expected) {
                    if (!(expected.getCause() instanceof IllegalStateException)) {
                        throw new AssertionError(expected);
                    }
                }
            }
            getServer().getOnlinePlayers(); // Still usable after pending-exception paths.
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
        var runtime = getServer().getAsyncRuntime();
        getServer().registerCommand(
                "reentrantdisable", "Reentrant lifecycle test", "", (sender, command, args) -> {
                    sender.sendMessage("reentrant-disable");
                    try {
                        getServer().getOnlinePlayers();
                    } catch (IllegalStateException expected) {
                        return true;
                    }
                    throw new AssertionError("Disabled owner remained usable");
                });
        runtime.submitIo(token -> {
            rejectWorkerService();
            virtual = Thread.currentThread().isVirtual();
            try {
                getServer().getOnlinePlayers();
            } catch (IllegalStateException expected) {
                wrongThreadRejected = true;
            }
            runtime.returnToServer(completed::incrementAndGet);
        });
        runtime.submitCpu(token -> {
            rejectWorkerService();
            runtime.returnToServer(completed::incrementAndGet);
        });
        getServer().registerCommand("asyncverify", "JNI async checks", "", (sender, command, args) -> {
            if (completed.get() != 2 || serviceRejected.get() != 2 || !virtual || !wrongThreadRejected) {
                return false;
            }
            sender.sendMessage(Component.translatable("zernyth.test", "native"));
            getServer().getOnlinePlayers().getFirst().sendMessage(
                    Component.translatable("zernyth.test", "player"));
            var p = getServer().getPerformanceManager().getPluginSnapshot();
            if (p.nativeBridgeCalls() < 1 || p.nativeBridgeExceptions() < 2 || p.callbacks().isEmpty()) {
                throw new AssertionError("Missing profile counters");
            }
            return true;
        });
    }

    private void rejectWorkerService() {
        try {
            getServer().getServicesManager().find(Runnable.class);
            throw new AssertionError("Worker service access accepted");
        } catch (IllegalStateException expected) {
            serviceRejected.incrementAndGet();
        }
    }
}
