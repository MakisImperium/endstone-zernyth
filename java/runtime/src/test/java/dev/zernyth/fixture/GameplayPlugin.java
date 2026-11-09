package dev.zernyth.fixture;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import dev.zernyth.api.command.Command;
import dev.zernyth.api.command.CommandSender;
import dev.zernyth.api.command.ConsoleSender;
import dev.zernyth.api.command.PlayerSender;
import dev.zernyth.api.event.BlockBreakEvent;
import dev.zernyth.api.event.BlockPlaceEvent;
import dev.zernyth.api.event.EventHandler;
import dev.zernyth.api.event.EventPriority;
import dev.zernyth.api.event.Listener;
import dev.zernyth.api.event.PlayerChatEvent;
import dev.zernyth.api.event.PlayerInteractEvent;
import dev.zernyth.api.event.PlayerJoinEvent;
import dev.zernyth.api.event.PlayerMoveEvent;
import dev.zernyth.api.event.PlayerQuitEvent;
import dev.zernyth.api.event.Registration;
import dev.zernyth.api.player.GameMode;
import dev.zernyth.api.player.Location;
import dev.zernyth.api.player.Player;
import dev.zernyth.api.plugin.JavaPlugin;
import dev.zernyth.api.scheduler.Task;

/**
 * Assertion-based fixture exercised by the native MockServer integration test.
 */
public final class GameplayPlugin extends JavaPlugin implements Listener {

    private Player player;
    private PlayerChatEvent retained;
    private CommandSender retainedSender;
    private final List<String> order = new ArrayList<>();
    private int joined, quit, moved, interacted, broken, placed, ignored;
    private int sync, delayed, repeated;
    private final AtomicInteger async = new AtomicInteger();
    private final AtomicInteger delayedAsync = new AtomicInteger(), repeatedAsync = new AtomicInteger();
    private Task timer;
    private Task asyncTimer;
    private Registration registration;
    private Command command;
    private Registration selfRegistration;
    private int selfCalls;

    public final class SelfRemoving implements Listener {

        @EventHandler
        public void join(PlayerJoinEvent e) {
            selfCalls++;
            selfRegistration.close();
        }
    }

    static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Gameplay fixture assertion failed");
        }
    }

    @Override
    public void onEnable() {
        player = getServer().getOnlinePlayers().getFirst();
        require(player.getName().equals("Alex"));
        require(player.getUniqueId().equals(new UUID(0, 0)));
        require(player.isOnline() && player.getHealth() == 20 && player.getGameMode() == GameMode.SURVIVAL);
        require(player.hasPermission("gameplay.use") && !player.hasPermission("denied"));
        Location from = player.getLocation();
        require(from.dimension().equals("overworld") && from.x() == 1 && from.pitch() == 10
                && from.yaw() == 20);
        require(player.teleport(new Location("overworld", 4, 5, 6, 30, 40)));
        try {
            player.teleport(new Location("missing", 4, 5, 6, 30, 40));
            throw new AssertionError("Unknown dimension accepted");
        } catch (IllegalStateException expected) {
        }
        player.setGameMode(GameMode.CREATIVE);
        player.sendMessage("player API ok 😀");
        player.kick("fixture kick");
        invalidHandle();
        registration = getServer().getPluginManager().registerEvents(this, this);
        selfRegistration = getServer().getPluginManager().registerEvents(new SelfRemoving(), this);
        command = getServer().registerCommand("gameplay", "Gameplay fixture", "gameplay.use", this::execute);
        Command temporary = getServer().registerCommand(
                "temporary", "Explicit close probe", "", (sender, cmd, args) -> true);
        temporary.close();
        temporary.close();
        Listener removed = new SelfRemoving();
        getServer().getPluginManager().registerEvents(removed, this);
        getServer().getPluginManager().unregisterEvents(removed, this);
        getServer().getScheduler().runTask(() -> sync++);
        getServer().getScheduler().runTaskLater(() -> delayed++, 2);
        timer = getServer().getScheduler().runTaskTimer(() -> repeated++, 0, 1);
        getServer().getScheduler().runTaskAsync(() -> {
            try {
                getServer().getOnlinePlayers();
                throw new AssertionError("Worker accessed gameplay API");
            } catch (IllegalStateException expected) {
                async.incrementAndGet();
            }
            getLogger().info("Async Java logging is safe");
        });
        getServer().getScheduler().runTaskAsync(
                () -> {
                    throw new IllegalArgumentException("expected async isolation probe");
                });
        getServer().getScheduler().runTaskLaterAsync(() -> delayedAsync.incrementAndGet(), 2);
        asyncTimer =
                getServer().getScheduler().runTaskTimerAsync(() -> repeatedAsync.incrementAndGet(), 0, 1);
        getServer().getScheduler().runTaskTimer(() -> {
        }, 1000, 1); // Cancelled automatically on disable.
        try {
            getServer().getScheduler().runTaskLater(() -> {
            }, -1);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
        }
    }

    private void invalidHandle() {
        try {
            var ownerField = getServer().getClass().getDeclaredField("owner");
            ownerField.setAccessible(true);
            long owner = ownerField.getLong(getServer());
            var method = Class.forName("dev.zernyth.runtime.Native")
                    .getDeclaredMethod("playerNumber", long.class, long.class, int.class);
            method.setAccessible(true);
            try {
                method.invoke(null, owner, Long.MAX_VALUE, 0);
                throw new AssertionError("Invalid player handle accepted");
            } catch (InvocationTargetException expected) {
                require(expected.getCause() instanceof IllegalStateException);
            }
            var players =
                    Class.forName("dev.zernyth.runtime.Native").getDeclaredMethod("players", long.class);
            players.setAccessible(true);
            try {
                players.invoke(null, Long.MAX_VALUE);
                throw new AssertionError("Invalid plugin owner accepted");
            } catch (InvocationTargetException expected) {
                require(expected.getCause() instanceof IllegalStateException);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private boolean execute(CommandSender sender, Command command, String[] args) {
        if (args.length == 0) {
            require(sender instanceof PlayerSender);
            require(((PlayerSender) sender).getPlayer().getName().equals("Alex"));
            require(sender.getName().equals("Alex") && sender.hasPermission("gameplay.use"));
            sender.sendMessage("command API ok");
            retainedSender = sender;
            return true;
        }
        switch (args[0]) {
            case "events" -> {
                require(selfCalls == 1);
                require(joined == 1 && quit == 1 && moved == 1 && interacted == 1 && broken == 1
                        && placed == 1 && ignored == 0);
                require(order.equals(List.of("low", "high", "highest", "monitor")));
                try {
                    retained.isCancelled();
                    throw new AssertionError("Retained event accepted");
                } catch (IllegalStateException expected) {
                }
                try {
                    retainedSender.sendMessage("expired");
                    throw new AssertionError("Retained sender accepted");
                } catch (IllegalStateException expected) {
                }
            }
            case "tasks" -> {
                require(sync == 1 && delayed == 1 && repeated >= 2 && async.get() == 1
                        && delayedAsync.get() == 1 && repeatedAsync.get() >= 2);
                timer.cancel();
                asyncTimer.cancel();
                require(timer.isCancelled() && asyncTimer.isCancelled());
            }
            case "offline" -> {
                require(!player.isOnline());
                try {
                    player.getHealth();
                    throw new AssertionError("Offline native player accepted");
                } catch (IllegalStateException expected) {
                }
            }
            case "unregister" -> {
                registration.close();
                registration.close();
                getServer().getScheduler().cancelTasks();
                Task cancelled = getServer().getScheduler().runTaskLater(() -> {
                }, 1000);
                cancelled.cancel();
                require(cancelled.isCancelled());
                getServer().getScheduler().runTaskTimer(() -> {
                }, 1000, 1);
            }
            case "console" -> {
                require(sender instanceof ConsoleSender && sender.getName().equals("Console"));
                sender.sendMessage("console API ok");
            }
            case "arguments" -> {
                require(Arrays.equals(args, new String[]{"arguments", "one", "two words"}));
            }
            case "throw" -> throw new IllegalArgumentException("expected command isolation probe");
            default -> throw new AssertionError("Unexpected command argument");
        }
        return true;
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        require(e.getPlayer().getName().equals("Alex"));
        joined++;
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        quit++;
    }

    @EventHandler
    public void move(PlayerMoveEvent e) {
        require(e.getFrom().x() == 1 && e.getTo().x() == 4);
        e.setCancelled(true);
        moved++;
    }

    @EventHandler
    public void interact(PlayerInteractEvent e) {
        require(e.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_AIR);
        e.setCancelled(true);
        interacted++;
    }

    @EventHandler
    public void breakBlock(BlockBreakEvent e) {
        require(e.getBlockLocation().x() == 1);
        e.setCancelled(true);
        broken++;
    }

    @EventHandler
    public void placeBlock(BlockPlaceEvent e) {
        require(e.getBlockLocation().x() == 1);
        e.setCancelled(true);
        placed++;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void chatLow(PlayerChatEvent e) {
        require(e.getMessage().equals("original"));
        e.setMessage("modified 😀");
        e.setCancelled(true);
        order.add("low");
        retained = e;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void chatThrows(PlayerChatEvent e) {
        require(e.isCancelled());
        order.add("high");
        throw new IllegalArgumentException("expected event isolation probe");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void chatHighest(PlayerChatEvent e) {
        require(e.getMessage().equals("modified 😀"));
        order.add("highest");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void chatMonitor(PlayerChatEvent e) {
        order.add("monitor");
        try {
            e.setCancelled(false);
            throw new AssertionError("MONITOR wrote cancellation");
        } catch (IllegalStateException expected) {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void ignored(PlayerChatEvent e) {
        ignored++;
    }

    @Override
    public void onDisable() {
        getLogger().info("Gameplay fixture disabled");
    }
}
