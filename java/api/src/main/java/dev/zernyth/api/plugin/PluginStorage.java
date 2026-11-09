package dev.zernyth.api.plugin;

import java.io.IOException;
import java.nio.file.Path;

import dev.zernyth.api.config.Configuration;

/**
 * Plugin-owned UTF-8 files. File operations require the lifecycle/server thread; no autosave on disable.
 */
public interface PluginStorage {

    /**
     * Returns the plugin data directory after storage validity checks.
     *
     * @return the plugin data directory
     */
    Path getDataFolder();

    /**
     * Loads the named YAML/JSON configuration or returns its cached object.
     *
     * @param name the declared name, or relative configuration filename for storage operations
     * @return the cached or loaded configuration
     * @throws java.io.IOException if bounded file/transport reading or writing fails
     */
    Configuration load(String name) throws IOException;

    /**
     * Explicitly reparses a named file; parse failure preserves the previous cached configuration.
     *
     * @param name the declared name, or relative configuration filename for storage operations
     * @throws java.io.IOException if bounded file/transport reading or writing fails
     */
    void reload(String name) throws IOException;

    /**
     * Explicitly writes the named cached configuration through the safe replacement path.
     *
     * @param name the declared name, or relative configuration filename for storage operations
     * @throws java.io.IOException if bounded file/transport reading or writing fails
     */
    void save(String name) throws IOException;

    /**
     * Copies a bundled default only when the target does not exist.
     *
     * @param name the declared name, or relative configuration filename for storage operations
     * @throws java.io.IOException if bounded file/transport reading or writing fails
     */
    void saveDefault(String name) throws IOException;
}
