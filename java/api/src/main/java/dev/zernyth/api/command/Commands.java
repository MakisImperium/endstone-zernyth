package dev.zernyth.api.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import dev.zernyth.api.Server;

/**
 * Registers declarative command trees through native commands, with rollback of partially registered aliases.
 */
public final class Commands {

    private Commands() {
    }

    /**
     * Registers this command tree or service provider with plugin ownership; partial command aliases are
     * rolled back.
     *
     * @param server the plugin-owned server facade
     * @param definition the immutable command tree
     * @return the closeable group registration
     */
    public static Command register(Server server, CommandDefinition definition) {
        var registered = new ArrayList<Command>();
        try {
            var names = new ArrayList<String>();
            names.add(definition.name());
            names.addAll(definition.aliases());
            for (var name : names) {
                registered.add(server.registerCommand(name, definition.description(), definition.permission(),
                        (sender, command, args) -> dispatch(definition, sender, List.of(args))));
            }
        } catch (Throwable error) {
            for (var command : registered) {
                command.close();
            }
            throw error;
        }
        return new Command() {
            public String getName() {
                return definition.name();
            }

            public void close() {
                registered.forEach(Command::close);
            }
        };
    }

    private static boolean dispatch(CommandDefinition node, CommandSender sender, List<String> args) {
        if (!node.permission().isEmpty() && !sender.hasPermission(node.permission())) {
            sender.sendMessage("You do not have permission.");
            return true;
        }
        if (node.playerOnly() && !(sender instanceof PlayerSender)) {
            sender.sendMessage("This command requires a player.");
            return true;
        }
        if (!node.children().isEmpty()) {
            if (!args.isEmpty()) {
                for (var child : node.children()) {
                    if (child.name().equals(args.getFirst()) || child.aliases().contains(args.getFirst())) {
                        return dispatch(child, sender, args.subList(1, args.size()));
                    }
                }
            }
            sender.sendMessage(node.name() + ": "
                    + String.join(", ", node.children().stream().map(CommandDefinition::name).toList()));
            return true;
        }
        if (args.size() != node.arguments().size()) {
            sender.sendMessage("Usage: " + node.name() + " "
                    + String.join(" ", node.arguments().stream().map(a -> "<" + a.name() + ">").toList()));
            return true;
        }
        var values = new LinkedHashMap<String, Object>();
        try {
            for (int i = 0; i < args.size(); i++) {
                var spec = node.arguments().get(i);
                String value = args.get(i);
                Object parsed = switch (spec.type()) {
                    case STRING -> value;
                    case INTEGER -> Integer.valueOf(value);
                    case DOUBLE -> {
                        double n = Double.parseDouble(value);
                        if (!Double.isFinite(n)) {
                            throw new IllegalArgumentException();
                        }
                        yield n;
                    }
                    case BOOLEAN -> {
                        if (!Set.of("true", "false").contains(value)) {
                            throw new IllegalArgumentException();
                        }
                        yield Boolean.valueOf(value);
                    }
                };
                values.put(spec.name(), parsed);
            }
        } catch (IllegalArgumentException error) {
            sender.sendMessage("Invalid command arguments.");
            return true;
        }
        return node.handler() == null || node.handler().execute(new CommandContext(sender, args, values));
    }
}
