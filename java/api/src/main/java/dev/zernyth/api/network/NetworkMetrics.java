package dev.zernyth.api.network;

/**
 * Per-plugin deliveries, not unique transport packets. Timing is zero when diagnostics are off.
 *
 * @param matchedEvents the native events matched for this plugin
 * @param callbacks the delivered callback count
 * @param callbackExceptions the observed callback exception count
 * @param wrongThreadDrops observations rejected on unsupported threads
 * @param slowCallbacks the native slow-callback count
 * @param bridgeNanos the cumulative native observation wall-clock nanoseconds
 * @param maxBridgeNanos the maximum native observation wall-clock nanoseconds
 * @param listenerNanos the cumulative measured Java listener wall-clock nanoseconds
 * @param maxListenerNanos the maximum measured Java listener wall-clock nanoseconds
 * @param diagnosticsEnabled whether native timing diagnostics are enabled
 */
public record NetworkMetrics(long matchedEvents, long callbacks, long callbackExceptions,
                             long wrongThreadDrops, long slowCallbacks, long bridgeNanos, long maxBridgeNanos,
                             long listenerNanos,
                             long maxListenerNanos, boolean diagnosticsEnabled) {

}
