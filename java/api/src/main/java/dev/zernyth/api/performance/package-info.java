/**
 * Plugin-owned performance and process-wide JVM observations.
 *
 * <p>Manager queries and configuration require the server thread and live owner.
 * Returned records are detached and worker-readable. Timings are wall-clock nanoseconds
 * unless a field explicitly states another unit; they are not CPU consumption or isolated
 * JNI transition costs. Concurrent counters are observational rather than a transactionally
 * consistent sample. GC and weak-reference counts do not prove complete classloader reclamation.
 */
package dev.zernyth.api.performance;
