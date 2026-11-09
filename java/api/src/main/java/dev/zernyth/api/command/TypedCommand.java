package dev.zernyth.api.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import dev.zernyth.api.Server;

/**
 * Immutable command tree; native registration/ownership remain in Server.
 * All dispatch and suggestion calls require the live server-thread sender.
 * Suggestions are local calculations, not native client tab-completion.
 */
public final class TypedCommand {

    /**
     * Immutable command argument description. Parser, range and default-value rules follow the enclosing
     * command type.
     *
     * @param name the declared name
     * @param type the argument value class
     * @param parser the token-to-value parser
     * @param suggestions the permitted suggestion values, copied
     * @param defaultValue the initial control value or optional argument default
     * @param <T> the argument or shared service value type
     */
    public record Argument<T>(String name, Class<T> type, Function<String, T> parser,
                              List<String> suggestions, Optional<T> defaultValue) {

        /**
         * Copies suggestions and checks any present default against the declared type; the parser runs only
         * during execution.
         *
         * @param name the declared name
         * @param type the argument value class
         * @param parser the token-to-value parser
         * @param suggestions the permitted suggestion values, copied
         * @param defaultValue the initial control value or optional argument default
         */
        public Argument {
            name = valid(name);
            Objects.requireNonNull(type);
            Objects.requireNonNull(parser);
            suggestions = List.copyOf(suggestions);
            Objects.requireNonNull(defaultValue);
            defaultValue.ifPresent(type::cast);
        }

        /**
         * Creates an argument with a required non-null default value.
         *
         * @param value the replacement value; null is permitted only where the enclosing contract specifies
         * @return the argument with a non-null default
         */
        public Argument<T> optional(T value) {
            return new Argument<>(name, type, parser, suggestions, Optional.of(value));
        }

        /**
         * Reads an integer argument or creates an argument with inclusive bounds, according to the signature.
         *
         * @param name the declared name
         * @param min the inclusive lower bound
         * @param max the inclusive upper bound
         * @return the parsed integer or bounded argument definition, according to the overload
         */
        public static Argument<Integer> integer(String name, int min, int max) {
            if (min > max) {
                throw new IllegalArgumentException("Invalid range");
            }
            return new Argument<>(name, Integer.class, value -> {
                int parsed = Integer.parseInt(value);
                if (parsed < min || parsed > max) {
                    throw new IllegalArgumentException("Expected " + min + ".." + max);
                }
                return parsed;
            }, List.of(), Optional.empty());
        }

        /**
         * Creates an argument constrained to a nonempty copied list of case-sensitive choices.
         *
         * @param name the declared name
         * @param choices the nonempty case-sensitive allowed tokens
         * @return the constrained argument
         */
        public static Argument<String> choice(String name, List<String> choices) {
            var allowed = List.copyOf(choices);
            if (allowed.isEmpty()) {
                throw new IllegalArgumentException("Empty choices");
            }
            return new Argument<>(name, String.class, value -> {
                if (!allowed.contains(value)) {
                    throw new IllegalArgumentException("Expected one of " + allowed);
                }
                return value;
            }, allowed, Optional.empty());
        }
    }

    /**
     * Parsed typed-command invocation. Collections are copied; the sender retains its native callback
     * lifetime.
     *
     * @param sender the callback-scoped native command sender
     * @param rawArguments the original argument tokens, copied
     * @param values the detached values; collection inputs are copied where specified
     */
    public record Context(CommandSender sender, List<String> rawArguments, Map<Argument<?>, Object> values) {

        /**
         * Copies raw and typed argument maps; the native sender is not detached or made worker-safe.
         *
         * @param sender the callback-scoped native command sender
         * @param rawArguments the original argument tokens, copied
         * @param values the detached values; collection inputs are copied where specified
         */
        public Context {
            Objects.requireNonNull(sender);
            rawArguments = List.copyOf(rawArguments);
            values = Map.copyOf(values);
        }

        /**
         * Reads a configuration path or typed command argument. Configuration returns null for missing/null
         * values.
         *
         * @param argument the typed argument key
         * @param <T> the argument or shared service value type
         * @return the requested value; missing configuration paths return null
         */
        public <T> T get(Argument<T> argument) {
            if (!values.containsKey(argument)) {
                throw new IllegalArgumentException("Argument not in this command: " + argument.name());
            }
            return argument.type().cast(values.get(argument));
        }
    }

    private final String name, description, permission;
    private final boolean playerOnly;
    private final List<String> aliases;
    private final List<Argument<?>> arguments;
    private final List<TypedCommand> children;
    private final Predicate<Context> handler;

    /**
     * Validates argument order and unambiguous child/alias names before registration; leaves require an
     * executor.
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
    public TypedCommand(String name, String description, String permission, boolean playerOnly,
            List<String> aliases, List<Argument<?>> arguments, List<TypedCommand> children,
            Predicate<Context> handler) {
        this.name = valid(name);
        this.description = Objects.requireNonNull(description);
        this.permission = Objects.requireNonNull(permission);
        this.playerOnly = playerOnly;
        this.aliases = List.copyOf(aliases);
        this.arguments = List.copyOf(arguments);
        this.children = List.copyOf(children);
        this.handler = handler;
        var names = new HashSet<String>();
        names.add(name);
        for (String alias : aliases) {
            if (!names.add(valid(alias))) {
                throw new IllegalArgumentException("Duplicate alias");
            }
        }
        names.clear();
        boolean optional = false;
        for (var argument : arguments) {
            if (!names.add(argument.name())) {
                throw new IllegalArgumentException("Duplicate argument");
            }
            if (optional && argument.defaultValue().isEmpty()) {
                throw new IllegalArgumentException("Required argument after optional");
            }
            optional |= argument.defaultValue().isPresent();
        }
        if (!arguments.isEmpty() && !children.isEmpty()) {
            throw new IllegalArgumentException("Branch arguments unsupported");
        }
        if (children.isEmpty() && handler == null) {
            throw new IllegalArgumentException("Missing executor");
        }
        names.clear();
        for (var child : children) {
            if (!names.add(child.name)) {
                throw new IllegalArgumentException("Duplicate child");
            }
            for (String alias : child.aliases) {
                if (!names.add(alias)) {
                    throw new IllegalArgumentException("Duplicate child alias");
                }
            }
        }
    }

    private static String valid(String name) {
        if (!Objects.requireNonNull(name).matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid name: " + name);
        }
        return name;
    }

    private boolean allowed(CommandSender sender) {
        return (permission.isEmpty() || sender.hasPermission(permission))
                && (!playerOnly || sender instanceof PlayerSender);
    }

    /**
     * Registers this command tree or service provider with plugin ownership; partial command aliases are
     * rolled back.
     *
     * @param server the plugin-owned server facade
     * @return the closeable group registration
     */
    public Command register(Server server) {
        var registrations = new ArrayList<Command>();
        try {
            var names = new ArrayList<>(aliases);
            names.addFirst(name);
            for (String label : names) {
                registrations.add(server.registerCommand(label, description, permission,
                        (sender, command, args) -> dispatch(sender, List.of(args), label)));
            }
        } catch (Throwable error) {
            for (var command : registrations) {
                try {
                    command.close();
                } catch (Throwable cleanup) {
                    error.addSuppressed(cleanup);
                }
            }
            throw error;
        }
        return new Command() {
            public String getName() {
                return name;
            }

            public void close() {
                registrations.forEach(Command::close);
            }
        };
    }

    /**
     * Executes the command under the enclosing command contract; validation failures produce user-facing
     * diagnostics.
     *
     * @param sender the callback-scoped native command sender
     * @param args the command argument tokens
     * @return the command result; invalid input is reported to the sender
     */
    public boolean execute(CommandSender sender, List<String> args) {
        return dispatch(sender, List.copyOf(args), name);
    }

    private boolean dispatch(CommandSender sender, List<String> args, String path) {
        if (!allowed(sender)) {
            sender.sendMessage(
                    playerOnly ? "Permission or player requirement not met." : "You do not have permission.");
            return true;
        }
        if (!children.isEmpty()) {
            if (!args.isEmpty()) {
                for (var child : children) {
                    if (child.name.equals(args.getFirst()) || child.aliases.contains(args.getFirst())) {
                        return child.dispatch(
                                sender, args.subList(1, args.size()), path + " " + args.getFirst());
                    }
                }
            }
            sender.sendMessage("Usage: " + path + " <"
                    + String.join(
                    "|", children.stream().filter(c -> c.allowed(sender)).map(c -> c.name).toList())
                    + ">");
            return true;
        }
        long required = arguments.stream().filter(a -> a.defaultValue().isEmpty()).count();
        if (args.size() < required || args.size() > arguments.size()) {
            sender.sendMessage("Usage: " + path + " "
                    + String.join(" ",
                    arguments.stream()
                            .map(a
                                    -> a.defaultValue().isPresent() ? "[" + a.name() + "]"
                                    : "<" + a.name() + ">")
                            .toList()));
            return true;
        }
        var values = new LinkedHashMap<Argument<?>, Object>();
        for (int i = 0; i < arguments.size(); i++) {
            var spec = arguments.get(i);
            try {
                Object value = i < args.size() ? Objects.requireNonNull(spec.parser().apply(args.get(i)))
                        : spec.defaultValue().orElseThrow();
                values.put(spec, spec.type().cast(value));
            } catch (IllegalArgumentException error) {
                sender.sendMessage(
                        "Invalid argument '" + spec.name() + "' for " + path + ": " + error.getMessage());
                return true;
            }
        }
        return handler.test(new Context(sender, args, values));
    }

    /**
     * Computes permitted local suggestions, not native dynamic client tab completion.
     *
     * @param sender the callback-scoped native command sender
     * @param tokens the partial argument tokens
     * @return the permitted local suggestions
     */
    public List<String> suggest(CommandSender sender, List<String> tokens) {
        tokens = List.copyOf(tokens);
        if (!allowed(sender)) {
            return List.of();
        }
        if (tokens.size() > 1 && !children.isEmpty()) {
            for (var child : children) {
                if (child.name.equals(tokens.getFirst()) || child.aliases.contains(tokens.getFirst())) {
                    return child.suggest(sender, tokens.subList(1, tokens.size()));
                }
            }
            return List.of();
        }
        String prefix = tokens.isEmpty() ? "" : tokens.getLast();
        if (!children.isEmpty()) {
            return children.stream()
                    .filter(c -> c.allowed(sender))
                    .map(c -> c.name)
                    .filter(n -> n.startsWith(prefix))
                    .toList();
        }
        int index = Math.max(0, tokens.size() - 1);
        return index < arguments.size() ? arguments.get(index)
                .suggestions()
                .stream()
                .filter(n -> n.startsWith(prefix))
                .limit(100)
                .toList()
                : List.of();
    }
}
