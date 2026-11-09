package dev.zernyth.api.plugin;

import java.util.List;

/**
 * Detached own-plugin diagnostics; no config values or absolute library paths.
 *
 * @param name the declared name
 * @param status the observed task/response lifecycle state
 * @param loadNanos the measured load wall-clock duration
 * @param enableNanos the measured enable wall-clock duration
 * @param disableNanos the measured disable wall-clock duration
 * @param classes the indexed plugin/library class count
 * @param libraries library names without absolute paths, copied
 * @param dependencies manifest dependency names, copied
 * @param eventListeners the owned event listener count
 * @param packetListeners the owned packet listener count
 * @param commands the owned command count
 * @param tasks the owned native task count
 * @param services the owned service registration count
 * @param lastFailureType the last diagnostic error classification, empty when absent
 */
public record
PluginDiagnostics(String name, String status, long loadNanos, long enableNanos, long disableNanos,
                  int classes, List<String> libraries, List<String> dependencies, long eventListeners,
                  long packetListeners, long commands, long tasks, long services, String lastFailureType) {

    /**
     * Copies library/dependency names for detached diagnostics; counters do not extend plugin lifetime.
     *
     * @param name the declared name
     * @param status the observed task/response lifecycle state
     * @param loadNanos the measured load wall-clock duration
     * @param enableNanos the measured enable wall-clock duration
     * @param disableNanos the measured disable wall-clock duration
     * @param classes the indexed plugin/library class count
     * @param libraries library names without absolute paths, copied
     * @param dependencies manifest dependency names, copied
     * @param eventListeners the owned event listener count
     * @param packetListeners the owned packet listener count
     * @param commands the owned command count
     * @param tasks the owned native task count
     * @param services the owned service registration count
     * @param lastFailureType the last diagnostic error classification, empty when absent
     */
    public PluginDiagnostics {
        libraries = List.copyOf(libraries);
        dependencies = List.copyOf(dependencies);
    }
}
