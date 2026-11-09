package dev.zernyth.api.command;

/**
 * Synchronous command callback. The native sender is valid only within its callback; do not block on I/O.
 */
@FunctionalInterface
public interface CommandExecutor {

    /**
     * Executes synchronously with a callback-scoped sender. The default JavaPlugin implementation reports a
     * missing executor.
     *
     * @param sender the callback-scoped native command sender
     * @param command the native command registration
     * @param args the command argument tokens
     * @return the executor result passed to native command dispatch
     */
    boolean onCommand(CommandSender sender, Command command, String[] args);
}
