package dev.example;

import java.io.IOException;
import java.util.List;

import dev.zernyth.api.command.TypedCommand;
import dev.zernyth.api.plugin.JavaPlugin;
import dev.zernyth.api.text.Component;

/**
 * Plugin-development starting point for lifecycle initialization, configuration and command registration.
 */
public final class ExamplePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            String label = getConfig().getString("server.name", "Zernyth Server");
            getLogger().info("Enabled " + label);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load configuration", error);
        }
        var count = TypedCommand.Argument.integer("count", 1, 100).optional(3);
        new TypedCommand("example", "Example command", "example.use", false, List.of("ex"), List.of(count),
                List.of(),
                context -> {
                    context.sender().sendMessage(
                            Component.text("Count: " + context.get(count)));
                    return true;
                })
                .register(getServer());
    }

    @Override
    public void onDisable() {
        getLogger().info("Example disabled");
    }
}
