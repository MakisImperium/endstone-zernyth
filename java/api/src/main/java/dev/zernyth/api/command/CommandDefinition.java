package dev.zernyth.api.command;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable command tree. Registration/execution require the server thread.
 *
 * @param name the declared name
 * @param description the description text or manifest metadata
 * @param permission the permission node; an empty command permission adds no extra node
 * @param playerOnly whether only player senders may execute this command
 * @param aliases additional command labels, copied
 * @param arguments the argument definitions or values, copied where specified
 * @param children nested subcommands, copied
 * @param handler the leaf executor; branches may use null
 */
public record CommandDefinition(String name, String description, String permission, boolean playerOnly,
                                List<String> aliases, List<Argument> arguments,
                                List<CommandDefinition> children, Handler handler) {

    /**
     * Synchronous server-thread executor for a parsed command context.
     */
    @FunctionalInterface
    public interface Handler {

        /**
         * Executes the command under the enclosing command contract; validation failures produce user-facing
         * diagnostics.
         *
         * @param context the detached observation or parsed command context
         * @return the command result; invalid input is reported to the sender
         */
        boolean execute(CommandContext context);
    }

    /**
     * Kinds supported by the enclosing API; constants do not imply support outside that API.
     */
    public enum Type {
        /**
         * String token argument.
         */
        STRING,
        /**
         * Integer token argument.
         */
        INTEGER,
        /**
         * Finite double token argument.
         */
        DOUBLE,
        /**
         * Boolean token argument.
         */
        BOOLEAN
    }

    /**
     * Immutable command argument description. Parser, range and default-value rules follow the enclosing
     * command type.
     *
     * @param name the declared name
     * @param type the required class or native type identifier, according to the signature
     */
    public record Argument(String name, Type type) {

        /**
         * Validates the argument label and requires a supported scalar type.
         *
         * @param name the declared name
         * @param type the required class or native type identifier, according to the signature
         */
        public Argument {
            Objects.requireNonNull(type);
            if (!name.matches("[a-z][a-z0-9_]*")) {
                throw new IllegalArgumentException("Invalid argument name");
            }
        }
    }

    /**
     * Validates labels, aliases and branch/leaf layouts before registration; collections are copied.
     *
     * @param name the declared name
     * @param description the description text or manifest metadata
     * @param permission the permission node; an empty command permission adds no extra node
     * @param playerOnly whether only player senders may execute this command
     * @param aliases additional command labels, copied
     * @param arguments the argument definitions or values, copied where specified
     * @param children nested subcommands, copied
     * @param handler the leaf executor; branches may use null
     */
    public CommandDefinition {
        Objects.requireNonNull(description);
        Objects.requireNonNull(permission);
        aliases = List.copyOf(aliases);
        arguments = List.copyOf(arguments);
        children = List.copyOf(children);
        if (!name.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid command name");
        }
        var names = new HashSet<String>();
        names.add(name);
        for (var alias : aliases) {
            if (!alias.matches("[a-z][a-z0-9_]*") || !names.add(alias)) {
                throw new IllegalArgumentException("Invalid/duplicate alias");
            }
        }
        var argNames = new HashSet<String>();
        for (var arg : arguments) {
            if (!argNames.add(arg.name())) {
                throw new IllegalArgumentException("Duplicate argument name");
            }
        }
        if (!children.isEmpty() && !arguments.isEmpty()) {
            throw new IllegalArgumentException("Branch nodes cannot have positional arguments");
        }
        if (children.isEmpty() && handler == null) {
            throw new IllegalArgumentException("Leaf commands require an executor");
        }
        names.clear();
        for (var child : children) {
            if (!names.add(child.name())) {
                throw new IllegalArgumentException("Duplicate subcommand");
            }
            for (var alias : child.aliases()) {
                if (!names.add(alias)) {
                    throw new IllegalArgumentException("Duplicate subcommand alias");
                }
            }
        }
    }
}
