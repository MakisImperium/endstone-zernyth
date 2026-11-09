package dev.zernyth.fixture;

import dev.zernyth.api.event.EventHandler;
import dev.zernyth.api.event.Listener;
import dev.zernyth.api.event.PlayerJoinEvent;
import dev.zernyth.api.plugin.JavaPlugin;

/**
 * Controlled lifecycle-failure fixture used by rollback regressions, not a deployment example.
 */
public final class FailingPlugin extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().registerCommand("partial", "Partial enable cleanup", "", (sender, command, args) -> true);
        getServer().getScheduler().runTaskTimer(() -> {
        }, 1000, 1);
        throw new IllegalStateException("Expected partial onEnable failure");
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
    }

    @Override
    public void onDisable() {
        throw new IllegalStateException("Expected cleanup despite onDisable failure");
    }
}
