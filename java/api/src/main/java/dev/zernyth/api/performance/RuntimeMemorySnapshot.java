package dev.zernyth.api.performance;

/**
 * Process-wide JVM memory/GC and weakly tracked plugin loaders, not per-plugin heap attribution.
 *
 * @param heapUsed the JVM-reported used heap bytes
 * @param heapCommitted the JVM-reported committed heap bytes
 * @param heapMax the JVM-reported maximum heap bytes, or its unavailable sentinel
 * @param loadedClasses the JVM-reported current loaded class count
 * @param unloadedClasses the JVM-reported cumulative unloaded class count
 * @param gcCollections the summed reported GC collection count
 * @param gcMillis the summed reported GC collection milliseconds
 * @param createdPluginLoaders the tracked loader creation count
 * @param closedPluginLoaders the tracked loader close count
 * @param weakLivePluginLoaders the weakly observable loader count, not a deterministic GC guarantee
 * @param openPluginLoaders the tracked loaders not explicitly closed
 */
public record RuntimeMemorySnapshot(long heapUsed, long heapCommitted, long heapMax, long loadedClasses,
                                    long unloadedClasses, long gcCollections, long gcMillis,
                                    long createdPluginLoaders,
                                    long closedPluginLoaders, long weakLivePluginLoaders,
                                    long openPluginLoaders) {

}
