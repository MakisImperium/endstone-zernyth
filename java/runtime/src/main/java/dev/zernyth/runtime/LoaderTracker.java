package dev.zernyth.runtime;

import java.lang.management.ManagementFactory;
import java.util.WeakHashMap;

import dev.zernyth.api.performance.RuntimeMemorySnapshot;

final class LoaderTracker {

    private static final WeakHashMap<ClassLoader, Boolean> LOADERS = new WeakHashMap<>();
    private static long created, closed;

    static synchronized void created(ClassLoader loader) {
        LOADERS.put(loader, false);
        created++;
    }

    static synchronized void closed(ClassLoader loader) {
        if (Boolean.FALSE.equals(LOADERS.put(loader, true))) {
            closed++;
        }
    }

    static synchronized RuntimeMemorySnapshot snapshot() {
        long open = LOADERS.values().stream().filter(v -> !v).count();
        long gc = 0, time = 0;
        for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (bean.getCollectionCount() < 0) {
                gc = -1;
            } else if (gc >= 0) {
                gc += bean.getCollectionCount();
            }
            if (bean.getCollectionTime() < 0) {
                time = -1;
            } else if (time >= 0) {
                time += bean.getCollectionTime();
            }
        }
        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        var classes = ManagementFactory.getClassLoadingMXBean();
        return new RuntimeMemorySnapshot(heap.getUsed(), heap.getCommitted(), heap.getMax(),
                classes.getLoadedClassCount(), classes.getUnloadedClassCount(), gc, time, created, closed,
                LOADERS.size(), open);
    }
}
