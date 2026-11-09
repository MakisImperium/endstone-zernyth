package dev.zernyth.runtime;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import dev.zernyth.api.command.CommandSender;
import dev.zernyth.api.command.TypedCommand;
import dev.zernyth.api.compat.PlayerAdapter;
import dev.zernyth.api.player.Player;
import dev.zernyth.api.text.Component;

final class DeveloperApiTest {

    static void check(boolean value) {
        if (!value) {
            throw new AssertionError("Developer API check");
        }
    }

    static void run() {
        var messages = new ArrayList<String>();
        var permitted = new AtomicBoolean(true);
        CommandSender sender = new CommandSender() {
            public String getName() {
                return "test";
            }

            public boolean hasPermission(String permission) {
                return permitted.get();
            }

            public void sendMessage(String text) {
                messages.add(text);
            }
        };
        var count = TypedCommand.Argument.integer("count", 1, 10).optional(2);
        var result = new AtomicInteger();
        var leaf = new TypedCommand(
                "give", "", "give.use", false, List.of("g"), List.of(count), List.of(), context -> {
            result.set(context.get(count));
            return true;
        });
        var root = new TypedCommand("root", "", "", false, List.of(), List.of(), List.of(leaf), null);
        root.execute(sender, List.of("g"));
        check(result.get() == 2);
        root.execute(sender, List.of("give", "8"));
        check(result.get() == 8);
        root.execute(sender, List.of("give", "11"));
        check(result.get() == 8 && messages.getLast().contains("count"));
        root.execute(sender, List.of("give", "1", "extra"));
        check(messages.getLast().contains("root give"));
        check(root.suggest(sender, List.of("g")).equals(List.of("give")));
        permitted.set(false);
        check(root.suggest(sender, List.of("")).isEmpty());
        root.execute(sender, List.of("give", "4"));
        check(result.get() == 8);
        sender.sendMessage(Component.text("plain"));
        check(messages.getLast().equals("plain"));
        var parameters = new ArrayList<>(List.of("a"));
        var translation = new Component.Translation("key", parameters);
        parameters.clear();
        check(translation.arguments().equals(List.of("a")));
        try {
            TypedCommand.Argument.integer("x", 4, 1);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
        }
        try {
            sender.sendMessage(translation);
            throw new AssertionError();
        } catch (UnsupportedOperationException expected) {
        }
        var nativeCalls = new AtomicInteger();
        var player = (Player) Proxy.newProxyInstance(
                DeveloperApiTest.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    nativeCalls.incrementAndGet();
                    return switch (method.getName()) {
                        case "getName" -> "adapted";
                        case "isOnline" -> true;
                        case "hasPermission" -> {
                            throw new IllegalStateException("expired owner");
                        }
                        default -> throw new UnsupportedOperationException();
                    };
                });
        var adapter = PlayerAdapter.adapt(player);
        check(adapter.getName().equals("adapted") && adapter.isOnline() && nativeCalls.get() == 2);
        try {
            adapter.hasPermission("test");
            throw new AssertionError();
        } catch (IllegalStateException expected) {
        }
        System.out.println("Developer API command/text checks passed (Java only).");
    }
}
