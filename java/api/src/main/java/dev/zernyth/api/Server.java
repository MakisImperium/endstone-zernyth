package dev.zernyth.api;

import java.util.List;

import dev.zernyth.api.command.Command;
import dev.zernyth.api.command.CommandExecutor;
import dev.zernyth.api.inventory.ItemStack;
import dev.zernyth.api.network.NetworkManager;
import dev.zernyth.api.performance.PerformanceManager;
import dev.zernyth.api.player.Player;
import dev.zernyth.api.plugin.PluginManager;
import dev.zernyth.api.scheduler.AsyncRuntime;
import dev.zernyth.api.scheduler.Scheduler;
import dev.zernyth.api.service.ServicesManager;
import dev.zernyth.api.world.World;

/**
 * Access to Endstone through the current plugin generation.
 *
 * <p>Native queries and mutations require the server thread and a live owning plugin.
 * Obtaining a lightweight manager facade does not itself extend or validate a native
 * object's lifetime; its operations perform their own checks. Player and world views
 * must not be used for native access from worker threads.
 *
 * <p>Zernyth's production implementation supplies every operation in this interface.
 * Defaults on later API additions preserve linkage for third-party implementations
 * written against older API versions. They are compatibility fallbacks, not feature
 * implementations. New implementations should override these operations explicitly.
 */
public interface Server {

    /**
     * Obtains and initializes this plugin's bounded task runtime on the server thread.
     *
     * @return the runtime owned by the current plugin generation
     * @throws IllegalStateException if native owner or server-thread validation fails
     * @throws UnsupportedOperationException if a legacy implementation does not supply this operation
     * @implSpec The default rejects access for compatibility with legacy implementations.
     * Zernyth's production implementation overrides it with its managed runtime.
     */
    default AsyncRuntime getAsyncRuntime() {
        throw new UnsupportedOperationException("Async runtime unavailable on this implementation");
    }

    /**
     * Obtains this plugin's performance facade. Configure and query it on the server thread;
     * returned snapshots may be read by workers.
     *
     * @return diagnostics for the current plugin generation
     * @throws UnsupportedOperationException if a legacy implementation does not supply this operation
     * @implSpec The compatibility default rejects access. Zernyth supplies a native-backed implementation.
     */
    default PerformanceManager getPerformanceManager() {
        throw new UnsupportedOperationException("Profiler unavailable on this implementation");
    }

    /**
     * Obtains the plugin-owned facade to Endstone's service registry. Registration,
     * lookup and service-proxy invocation retain their server-thread and ownership checks.
     *
     * @return the services facade for this owner
     * @throws UnsupportedOperationException if a legacy implementation does not supply this operation
     * @implSpec The compatibility default rejects access. Zernyth supplies native service registration
     * and revocable provider proxies.
     */
    default ServicesManager getServicesManager() {
        throw new UnsupportedOperationException("Services unavailable on this implementation");
    }

    /**
     * Obtains read-only packet metadata observation. Query the manager's capabilities before
     * registering observations; a facade alone does not promise support for a BDS version.
     *
     * @return this plugin's networking facade
     * @throws UnsupportedOperationException if a legacy implementation does not supply this operation
     * @implSpec The compatibility default rejects access. Zernyth supplies its existing native adapter.
     */
    default NetworkManager getNetworkManager() {
        throw new UnsupportedOperationException("Networking unavailable on this implementation");
    }

    /**
     * Queries players currently online on the server thread.
     *
     * @return an unmodifiable list of session-checked player views; an empty list if none are online
     * @throws IllegalStateException if the native owner or calling thread is invalid
     */
    List<Player> getOnlinePlayers();

    /**
     * Obtains the owner-scoped listener-registration facade to the existing plugin manager.
     * Registrations must be created and removed on the server thread.
     *
     * @return this plugin's plugin-manager facade
     */
    PluginManager getPluginManager();

    /**
     * Obtains this plugin's facade to the existing Endstone scheduler.
     * Task registration and native task cancellation require the server thread.
     *
     * @return this plugin's scheduler facade
     */
    Scheduler getScheduler();

    /**
     * Registers a command in the existing native command system on the server thread.
     * The current plugin owns its callback, which is removed on disable.
     *
     * @param name the command label, matching {@code [a-z][a-z0-9_]*}
     * @param description the command description, non-null
     * @param permission the permission node, or an empty string for no additional command permission
     * @param executor the synchronous command callback, non-null
     * @return a registration that unregisters this command when closed
     * @throws NullPointerException if a required argument is null
     * @throws IllegalArgumentException if the label or native command definition is invalid
     * @throws IllegalStateException if native registration, owner or thread validation fails
     */
    Command registerCommand(String name, String description, String permission, CommandExecutor executor);

    /**
     * Queries native world views on the server thread without loading or generating chunks.
     * Each view revalidates its native level identity when used.
     *
     * @return an unmodifiable list of available world views
     * @throws IllegalStateException if the native owner or calling thread is invalid
     * @throws UnsupportedOperationException if a legacy implementation does not supply this operation
     * @implSpec The compatibility default rejects access. Zernyth resolves the existing native level.
     */
    default List<World> getWorlds() {
        throw new UnsupportedOperationException("Advanced world API unavailable on this implementation");
    }

    /**
     * Creates an immutable item snapshot using the native item registry on the server thread.
     * The returned value contains no native pointer and may be retained by workers.
     *
     * @param type the namespaced native item identifier, such as {@code minecraft:stone}
     * @param amount the positive amount, within the native item's maximum stack size
     * @return a detached native-validated item snapshot
     * @throws NullPointerException if the identifier is null
     * @throws IllegalArgumentException if the identifier or amount is invalid
     * @throws IllegalStateException if native creation, owner or thread validation fails
     * @throws UnsupportedOperationException if a legacy implementation does not supply this operation
     * @implSpec The compatibility default rejects access. Zernyth creates the item through Endstone.
     */
    default ItemStack createItemStack(String type, int amount) {
        throw new UnsupportedOperationException("Advanced item API unavailable on this implementation");
    }
}
