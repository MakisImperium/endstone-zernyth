package dev.zernyth.fixture.ecosystem;

import dev.zernyth.api.plugin.JavaPlugin;
import dev.zernyth.api.service.ServicePriority;
import dev.zernyth.fixture.shared.Greeting;

/**
 * Provides the shared greeting service in dependency, classloader and revocation regressions.
 */
public final class ProviderPlugin extends JavaPlugin {

    @Override
    public void onLoad() {
        if (getDescription().name().equals("bad_load")) {
            throw new IllegalStateException("expected load failure");
        }
        if (getDescription().name().equals("eco_provider")) {
            try {
                getServer().getServicesManager().register(
                        Greeting.class, () -> "not enabled", ServicePriority.HIGH);
                throw new AssertionError("Service registered during onLoad");
            } catch (IllegalStateException expected) {
            }
        }
    }

    @Override
    public void onEnable() {
        if (!getDescription().name().equals("eco_provider") &&
                !getDescription().name().equals("bad_enable")) {
            return;
        }
        var services = getServer().getServicesManager();
        services.register(Greeting.class, () -> "low", ServicePriority.LOW);
        services.register(Greeting.class, () -> "high", ServicePriority.HIGH);
        if (getDescription().name().equals("bad_enable")) {
            getServer().registerCommand("orphan", "", "", (sender, c, args) -> true);
            getServer().getScheduler().runTaskTimer(() -> {
            }, 1, 1);
            throw new IllegalStateException("expected enable failure");
        }
    }

    @Override
    public void onDisable() {
        if (getDescription().name().equals("bad_enable")) {
            throw new IllegalStateException("expected disable failure");
        }
    }
}
