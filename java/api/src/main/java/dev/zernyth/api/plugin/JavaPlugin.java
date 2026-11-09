package dev.zernyth.api.plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

import dev.zernyth.api.Server;
import dev.zernyth.api.command.Command;
import dev.zernyth.api.command.CommandSender;
import dev.zernyth.api.config.Configuration;

/**
 * Lifecycle runs on the server thread. API use before initialization fails explicitly.
 */
public abstract class JavaPlugin {

    /**
     * Creates a plugin instance. The runtime injects its context before invoking lifecycle hooks.
     */
    public JavaPlugin() {
    }

    private PluginDescription description;
    private Logger logger;
    private Server server;
    private PluginStorage storage;
    private Supplier<PluginDiagnostics> diagnostics;

    /**
     * Runtime-only storage/diagnostic injection; a second call is rejected.
     *
     * @param storage the plugin-owned storage implementation
     * @param diagnostics the own-plugin diagnostic supplier
     */
    public final void initializeStorage(
            PluginStorage storage, Supplier<PluginDiagnostics> diagnostics) {
        if (this.storage != null) {
            throw new IllegalStateException("Storage already initialized");
        }
        this.storage = Objects.requireNonNull(storage);
        this.diagnostics = Objects.requireNonNull(diagnostics);
    }

    /**
     * Returns the injected storage; access before injection fails.
     *
     * @return the injected storage
     */
    public final PluginStorage getStorage() {
        return Objects.requireNonNull(storage, "Storage not initialized");
    }

    /**
     * Returns the plugin data directory after storage validity checks.
     *
     * @return the plugin data directory
     */
    public final Path getDataFolder() {
        return getStorage().getDataFolder();
    }

    /**
     * Loads or returns cached config.yml without saving it.
     *
     * @return the cached or loaded configuration
     * @throws java.io.IOException if bounded file/transport reading or writing fails
     */
    public final Configuration getConfig() throws IOException {
        return getStorage().load("config.yml");
    }

    /**
     * Copies bundled config.yml only when the destination is absent.
     *
     * @throws java.io.IOException if bounded file/transport reading or writing fails
     */
    public final void saveDefaultConfig() throws IOException {
        getStorage().saveDefault("config.yml");
    }

    /**
     * Explicitly reloads config.yml; obtain getConfig again to use the replacement cached object.
     *
     * @throws java.io.IOException if bounded file/transport reading or writing fails
     */
    public final void reloadConfig() throws IOException {
        getStorage().reload("config.yml");
    }

    /**
     * Explicitly saves cached config.yml.
     *
     * @throws java.io.IOException if bounded file/transport reading or writing fails
     */
    public final void saveConfig() throws IOException {
        getStorage().save("config.yml");
    }

    /**
     * Returns detached own-plugin lifecycle and resource diagnostics.
     *
     * @return the detached own-plugin diagnostics
     */
    public final PluginDiagnostics getDiagnostics() {
        return Objects.requireNonNull(diagnostics, "Diagnostics not initialized").get();
    }

    /**
     * Executes synchronously with a callback-scoped sender. The default JavaPlugin implementation reports a
     * missing executor.
     *
     * @param sender the callback-scoped native command sender
     * @param command the native command registration
     * @param args the command argument tokens
     * @return the executor result passed to native command dispatch
     */
    public boolean onCommand(CommandSender sender,
            Command command, String[] args) {
        sender.sendMessage("No executor configured for " + command.getName());
        return true;
    }

    /**
     * Returns the injected plugin-owned server facade.
     *
     * @return the injected server facade
     */
    public final Server getServer() {
        return Objects.requireNonNull(server, "Server not initialized");
    }

    /**
     * Runtime-only injection; API consumers do not construct implementations.
     *
     * @param server the plugin-owned server facade
     */
    public final void initializeServer(Server server) {
        if (this.server != null) {
            throw new IllegalStateException("Server already initialized");
        }
        this.server = Objects.requireNonNull(server);
    }

    /**
     * Returns the manifest description injected before lifecycle callbacks.
     *
     * @return the injected manifest description
     */
    public final PluginDescription getDescription() {
        return Objects.requireNonNull(description, "Plugin not initialized");
    }

    /**
     * Returns the injected plugin logger; logging is permitted on Java workers.
     *
     * @return the plugin logger
     */
    public final Logger getLogger() {
        return Objects.requireNonNull(logger, "Plugin not initialized");
    }

    /**
     * Runtime entry point; may only be called once.
     *
     * @param description the description text or manifest metadata
     * @param logger the plugin logger
     */
    public final void initialize(PluginDescription description, Logger logger) {
        if (this.description != null) {
            throw new IllegalStateException("Already initialized");
        }
        this.description = Objects.requireNonNull(description);
        this.logger = Objects.requireNonNull(logger);
    }

    /**
     * Runs on the server thread after construction and injection, before activation.
     */
    public void onLoad() {
    }

    /**
     * Runs when the native manager permits activation. Register plugin-owned resources here.
     */
    public void onEnable() {
    }

    /**
     * Runs during deactivation. Do not wait on workers or force-stop Java code.
     */
    public void onDisable() {
    }
}
