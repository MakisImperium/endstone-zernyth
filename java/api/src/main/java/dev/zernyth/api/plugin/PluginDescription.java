package dev.zernyth.api.plugin;

import java.util.List;

/**
 * Loader-supplied manifest metadata. Dependency lists are copied; native Endstone owns dependency resolution.
 *
 * @param name the declared name
 * @param version the manifest version string
 * @param main the fully qualified plugin main class
 * @param depend required plugin names, copied
 * @param softDepend optional plugin names, copied
 * @param loadBefore plugin names to load after this plugin, copied
 */
public record PluginDescription(String name, String version, String main, List<String> depend,
                                List<String> softDepend, List<String> loadBefore) {

    /**
     * Copies dependency lists. Manifest-field validation is performed separately by the loader.
     *
     * @param name the declared name
     * @param version the manifest version string
     * @param main the fully qualified plugin main class
     * @param depend required plugin names, copied
     * @param softDepend optional plugin names, copied
     * @param loadBefore plugin names to load after this plugin, copied
     */
    public PluginDescription {
        depend = List.copyOf(depend);
        softDepend = List.copyOf(softDepend);
        loadBefore = List.copyOf(loadBefore);
    }
}
