package dev.zernyth.api.plugin;

import dev.zernyth.api.event.Listener;
import dev.zernyth.api.event.Registration;

/**
 * Registers Java handlers in the existing native event infrastructure. Registration and removal require the
 * server thread and the matching owner plugin.
 */
public interface PluginManager {

    /**
     * Registers annotated handlers with the matching owner; partial failures are rolled back.
     *
     * @param listener the handler object, non-null
     * @param owner the plugin owning the registration; must match the facade owner
     * @return a registration for this listener group
     */
    Registration registerEvents(Listener listener, JavaPlugin owner);

    /**
     * Removes registration groups associated with this listener and matching plugin.
     *
     * @param listener the handler object, non-null
     * @param owner the plugin owning the registration; must match the facade owner
     */
    void unregisterEvents(Listener listener, JavaPlugin owner);
}
