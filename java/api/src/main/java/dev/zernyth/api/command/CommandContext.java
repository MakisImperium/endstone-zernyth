package dev.zernyth.api.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sender is valid only inside the native command callback. Parsed values and argument copies are detached.
 *
 * @param sender the callback-scoped native command sender
 * @param rawArguments the original argument tokens, copied
 * @param arguments the argument definitions or values, copied where specified
 */
public record CommandContext(CommandSender sender, List<String> rawArguments, Map<String, Object> arguments) {

    /**
     * Copies raw and parsed arguments while retaining the sender's original callback-scoped lifetime.
     *
     * @param sender the callback-scoped native command sender
     * @param rawArguments the original argument tokens, copied
     * @param arguments the argument definitions or values, copied where specified
     */
    public CommandContext {
        Objects.requireNonNull(sender);
        rawArguments = List.copyOf(rawArguments);
        arguments = Map.copyOf(arguments);
    }

    /**
     * Reads a named command argument as a string.
     *
     * @param name the declared name
     * @return the parsed string
     */
    public String string(String name) {
        return (String) arguments.get(name);
    }

    /**
     * Reads a named argument as an integer. Missing values cannot be unboxed and wrong types are rejected.
     *
     * @param name the declared name
     * @return the parsed integer
     * @throws NullPointerException if the named value is missing or null
     * @throws ClassCastException if the named value is not an Integer
     */
    public int integer(String name) {
        return (Integer) arguments.get(name);
    }

    /**
     * Reads a parsed decimal command argument.
     *
     * @param name the declared name
     * @return the parsed double
     */
    public double decimal(String name) {
        return (Double) arguments.get(name);
    }

    /**
     * Reads a parsed boolean command argument.
     *
     * @param name the declared name
     * @return the parsed boolean
     */
    public boolean bool(String name) {
        return (Boolean) arguments.get(name);
    }
}
