package dev.zernyth.runtime;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import dev.zernyth.api.Server;
import dev.zernyth.api.command.Command;
import dev.zernyth.api.command.CommandExecutor;
import dev.zernyth.api.command.CommandSender;
import dev.zernyth.api.command.ConsoleSender;
import dev.zernyth.api.command.PlayerSender;
import dev.zernyth.api.entity.Entity;
import dev.zernyth.api.event.BlockBreakEvent;
import dev.zernyth.api.event.BlockPlaceEvent;
import dev.zernyth.api.event.Event;
import dev.zernyth.api.event.EventHandler;
import dev.zernyth.api.event.Listener;
import dev.zernyth.api.event.PlayerChatEvent;
import dev.zernyth.api.event.PlayerInteractEvent;
import dev.zernyth.api.event.PlayerJoinEvent;
import dev.zernyth.api.event.PlayerMoveEvent;
import dev.zernyth.api.event.PlayerQuitEvent;
import dev.zernyth.api.event.Registration;
import dev.zernyth.api.form.Form;
import dev.zernyth.api.form.FormRequest;
import dev.zernyth.api.inventory.Inventory;
import dev.zernyth.api.inventory.ItemStack;
import dev.zernyth.api.network.NetworkManager;
import dev.zernyth.api.performance.PerformanceManager;
import dev.zernyth.api.performance.PerformanceOptions;
import dev.zernyth.api.performance.PluginPerformanceSnapshot;
import dev.zernyth.api.performance.RuntimeMemorySnapshot;
import dev.zernyth.api.player.GameMode;
import dev.zernyth.api.player.Location;
import dev.zernyth.api.player.Player;
import dev.zernyth.api.plugin.JavaPlugin;
import dev.zernyth.api.plugin.PluginManager;
import dev.zernyth.api.scheduler.AsyncRuntime;
import dev.zernyth.api.scheduler.Scheduler;
import dev.zernyth.api.scheduler.Task;
import dev.zernyth.api.service.ServicesManager;
import dev.zernyth.api.text.Component;
import dev.zernyth.api.text.Component.Text;
import dev.zernyth.api.text.Component.Translation;
import dev.zernyth.api.world.World;

final class GameServer implements Server, PluginManager, Scheduler {

    private final long owner;
    private final JavaPlugin plugin;
    private final Advanced advanced;
    private final Network network;
    private final Profile profile;
    private final ManagedRuntime async;

    @Override
    public ServicesManager getServicesManager() {
        return new Services(owner);
    }

    private final Map<Listener, List<Registration>> listeners = new IdentityHashMap<>();

    GameServer(long owner, JavaPlugin plugin) {
        this.owner = owner;
        this.plugin = plugin;
        advanced = new Advanced(owner, this::player);
        profile = new Profile(message -> plugin.getLogger().warning(message));
        network = new Network(owner, profile);
        async = new ManagedRuntime(new ManagedRuntime.Host() {
            public void checkMain() {
                Native.validate(owner);
            }

            public Task pulse(Runnable action) {
                return nativeSchedule(action, 0, 1, false);
            }

            public Task cpu(Runnable action) {
                return nativeSchedule(action, 0, 0, true);
            }
        }, plugin.getClass().getClassLoader(), owner, profile);
    }

    void activateRuntime() {
        async.activate();
    }

    void stopRuntime() {
        async.close();
    }

    @Override
    public AsyncRuntime getAsyncRuntime() {
        async.ensureStarted();
        return async;
    }

    @Override
    public PerformanceManager getPerformanceManager() {
        return new PerformanceManager() {
            public void configure(PerformanceOptions options) {
                Objects.requireNonNull(options);
                Native.performance(owner, options.enabled() ? 1 : 0);
                profile.options = options;
            }

            public PluginPerformanceSnapshot getPluginSnapshot() {
                long[] values = Native.performance(owner, -1);
                return new PluginPerformanceSnapshot(owner, profile.snapshot(),
                        async.snapshot(), values[0], values[1], values[2], values[3]);
            }

            public RuntimeMemorySnapshot getRuntimeMemorySnapshot() {
                Native.validate(owner);
                return LoaderTracker.snapshot();
            }
        };
    }

    @Override
    public NetworkManager getNetworkManager() {
        return network;
    }

    @Override
    public List<World> getWorlds() {
        return advanced.worlds();
    }

    @Override
    public ItemStack createItemStack(String type, int amount) {
        return advanced.createItem(type, amount);
    }

    public PluginManager getPluginManager() {
        return this;
    }

    public Scheduler getScheduler() {
        return this;
    }

    public List<Player> getOnlinePlayers() {
        return Arrays.stream(Native.players(owner)).mapToObj(this::player).toList();
    }

    private Player player(long handle) {
        return new NativePlayer(handle);
    }

    private static Location location(String[] values) {
        return new Location(values[0], Double.parseDouble(values[1]), Double.parseDouble(values[2]),
                Double.parseDouble(values[3]), Float.parseFloat(values[4]), Float.parseFloat(values[5]));
    }

    private final class NativePlayer implements Player {

        private final long handle;

        NativePlayer(long handle) {
            this.handle = handle;
        }

        @Override
        public Inventory getInventory() {
            return advanced.inventory(handle, false);
        }

        @Override
        public Inventory getEnderChest() {
            return advanced.inventory(handle, true);
        }

        @Override
        public Entity asEntity() {
            return advanced.playerEntity(handle);
        }

        @Override
        public FormRequest showForm(Form form) {
            return advanced.showForm(handle, form);
        }

        public String getName() {
            return Native.playerData(owner, handle, 0)[0];
        }

        public UUID getUniqueId() {
            return UUID.fromString(Native.playerData(owner, handle, 0)[1]);
        }

        public boolean isOnline() {
            return Native.playerNumber(owner, handle, 0) != 0;
        }

        public void sendMessage(String text) {
            Native.playerAction(owner, handle, 0, Objects.requireNonNull(text), null);
        }

        public void sendMessage(Component message) {
            component(handle, false, message);
        }

        public void kick(String text) {
            Native.playerAction(owner, handle, 1, Objects.requireNonNull(text), null);
        }

        public Location getLocation() {
            return location(Native.playerData(owner, handle, 1));
        }

        public boolean teleport(Location to) {
            Objects.requireNonNull(to);
            return Native.playerAction(owner, handle, 2, to.dimension(),
                    new double[]{to.x(), to.y(), to.z(), to.pitch(), to.yaw()});
        }

        public GameMode getGameMode() {
            return GameMode.values()[Native.playerNumber(owner, handle, 2)];
        }

        public void setGameMode(GameMode mode) {
            Native.playerAction(owner, handle, 3, "", new double[]{Objects.requireNonNull(mode).ordinal()});
        }

        public int getHealth() {
            return Native.playerNumber(owner, handle, 1);
        }

        public boolean hasPermission(String permission) {
            return Native.permission(owner, handle, Objects.requireNonNull(permission), false);
        }
    }

    public Registration registerEvents(Listener listener, JavaPlugin ownerPlugin) {
        if (ownerPlugin != plugin) {
            throw new IllegalArgumentException("Foreign plugin owner");
        }
        Objects.requireNonNull(listener);
        record Handler(Method method, EventHandler annotation, Class<?> event) {

        }
        var methods = new ArrayList<Handler>();
        for (Method method : listener.getClass().getMethods()) {
            var annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != void.class || method.getParameterCount() != 1
                    || !supported(method.getParameterTypes()[0])) {
                throw new IllegalArgumentException("Invalid @EventHandler: " + method);
            }
            if (!method.trySetAccessible()) {
                throw new IllegalArgumentException("Handler inaccessible: " + method);
            }
            methods.add(new Handler(method, annotation, method.getParameterTypes()[0]));
        }
        methods.sort(Comparator.comparing(h -> h.method().toGenericString()));
        var registrations = new ArrayList<Registration>();
        try {
            for (var handler : methods) {
                long token = Native.subscribe(owner, handler.event().getSimpleName(),
                        handler.annotation().priority().ordinal(), handler.annotation().ignoreCancelled(),
                        (event, player) -> {
                            try {
                                profile.measure("event", handler.event().getSimpleName(), () -> {
                                    handler.method().invoke(listener, event(handler.event(), event, player));
                                    return null;
                                });
                            } catch (CompletionException e) {
                                throw new RuntimeException("Java event listener failed", e.getCause());
                            }
                        });
                registrations.add(() -> Native.unsubscribe(owner, token));
            }
        } catch (Throwable failure) {
            for (var registration : registrations) {
                registration.close();
            }
            throw failure;
        }
        var group = new Registration() {
            boolean closed;

            public void close() {
                if (!closed) {
                    for (var registration : registrations) {
                        registration.close();
                    }
                    closed = true;
                    var groups = listeners.get(listener);
                    if (groups != null) {
                        groups.remove(this);
                        if (groups.isEmpty()) {
                            listeners.remove(listener);
                        }
                    }
                }
            }
        };
        listeners.computeIfAbsent(listener, k -> new ArrayList<>()).add(group);
        return group;
    }

    private static boolean supported(Class<?> event) {
        return Set
                .of(PlayerJoinEvent.class, PlayerQuitEvent.class, PlayerChatEvent.class,
                        PlayerMoveEvent.class, PlayerInteractEvent.class, BlockBreakEvent.class,
                        BlockPlaceEvent.class)
                .contains(event);
    }

    public void unregisterEvents(Listener listener, JavaPlugin ownerPlugin) {
        if (ownerPlugin != plugin) {
            throw new IllegalArgumentException("Foreign plugin owner");
        }
        for (var registration : List.copyOf(listeners.getOrDefault(listener, List.of()))) {
            registration.close();
        }
    }

    void reset() {
        listeners.clear();
    }

    private Event event(Class<?> type, long handle, long playerHandle) {
        Player player = player(playerHandle);
        BooleanSupplier read = () -> Native.cancellation(owner, handle, false, false);
        Consumer<Boolean> write = value -> Native.cancellation(owner, handle, true, value);
        if (type == PlayerJoinEvent.class) {
            return new PlayerJoinEvent(player);
        }
        if (type == PlayerQuitEvent.class) {
            return new PlayerQuitEvent(player);
        }
        if (type == PlayerChatEvent.class) {
            return new PlayerChatEvent(player, read, write,
                    ()
                            -> Native.eventText(owner, handle, false, null),
                    value -> Native.eventText(owner, handle, true, value));
        }
        if (type == PlayerMoveEvent.class) {
            return new PlayerMoveEvent(player, read, write, location(Native.eventData(owner, handle, 0)),
                    location(Native.eventData(owner, handle, 1)));
        }
        if (type == PlayerInteractEvent.class) {
            return new PlayerInteractEvent(player, read, write,
                    PlayerInteractEvent.Action
                            .values()[Integer.parseInt(Native.eventData(owner, handle, 3)[0])]);
        }
        Location block = location(Native.eventData(owner, handle, 2));
        if (type == BlockBreakEvent.class) {
            return new BlockBreakEvent(player, read, write, block);
        }
        return new BlockPlaceEvent(player, read, write, block);
    }

    public Command registerCommand(
            String name, String description, String permission, CommandExecutor executor) {
        Objects.requireNonNull(executor);
        if (!Objects.requireNonNull(name).matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid command name");
        }
        final Command[] command = new Command[1];
        long token = Native.command(owner, name, Objects.requireNonNull(description),
                Objects.requireNonNull(permission),
                (sender, args)
                        -> profile.measure(
                        "command", name, () -> executor.onCommand(sender(sender), command[0], args)));
        command[0] = new Command() {
            public String getName() {
                return name;
            }

            public void close() {
                Native.removeCommand(owner, token);
            }
        };
        return command[0];
    }

    private CommandSender sender(long handle) {
        String[] data = Native.senderData(owner, handle);
        class Sender implements CommandSender {

            public String getName() {
                return Native.senderData(owner, handle)[0];
            }

            public void sendMessage(String text) {
                Native.senderMessage(owner, handle, Objects.requireNonNull(text));
            }

            public void sendMessage(Component message) {
                component(handle, true, message);
            }

            public boolean hasPermission(String permission) {
                return Native.permission(owner, handle, Objects.requireNonNull(permission), true);
            }
        }
        if (data[1].equals("player")) {
            class PlayerView extends Sender implements PlayerSender {

                public Player getPlayer() {
                    return player(Long.parseLong(Native.senderData(owner, handle)[2]));
                }
            }
            return new PlayerView();
        }
        if (data[1].equals("console")) {
            class ConsoleView extends Sender implements ConsoleSender {

            }
            return new ConsoleView();
        }
        return new Sender();
    }

    private void component(long subject, boolean sender, Component message) {
        Objects.requireNonNull(message);
        if (message instanceof Text text) {
            if (sender) {
                Native.senderMessage(owner, subject, text.content());
            } else {
                Native.playerAction(owner, subject, 0, text.content(), null);
            }
        } else if (message instanceof Translation text) {
            Native.translation(owner, subject, sender, text.key(), text.arguments().toArray(String[]::new));
        }
    }

    private Task nativeSchedule(Runnable action, long delay, long period, boolean asynchronous) {
        Objects.requireNonNull(action);
        if (delay < 0 || period < 0) {
            throw new IllegalArgumentException("Negative ticks");
        }
        var finished = new AtomicBoolean();
        var cancelled = new AtomicBoolean();
        long token = Native.schedule(owner, () -> {
            try {
                Objects.requireNonNull(action).run();
            } finally {
                if (period == 0) {
                    finished.set(true);
                }
            }
        }, delay, period, asynchronous);
        return new Task() {
            public void cancel() {
                Native.validate(owner);
                cancelled.set(true);
                if (!finished.get()) {
                    try {
                        Native.task(owner, token, true);
                    } catch (IllegalStateException error) {
                        Native.validate(owner);
                        if (!"Invalid task handle".equals(error.getMessage())) {
                            throw error;
                        }
                    }
                }
            }

            public boolean isCancelled() {
                if (cancelled.get() || finished.get()) {
                    Native.validate(owner);
                    return cancelled.get();
                }
                try {
                    return Native.task(owner, token, false);
                } catch (IllegalStateException error) {
                    Native.validate(owner);
                    if (!"Invalid task handle".equals(error.getMessage())) {
                        throw error;
                    }
                    cancelled.set(true);
                    return true;
                }
            }
        };
    }

    private Task schedule(Runnable action, long delay, long period, boolean asynchronous) {
        Objects.requireNonNull(action);
        ClassLoader context = plugin.getClass().getClassLoader();
        return nativeSchedule(() -> {
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            try {
                thread.setContextClassLoader(context);
                profile.measure("legacy-task", asynchronous ? "async" : "sync", () -> {
                    action.run();
                    return null;
                });
            } finally {
                thread.setContextClassLoader(previous);
            }
        }, delay, period, asynchronous);
    }

    public Task runTask(Runnable r) {
        return schedule(r, 0, 0, false);
    }

    public Task runTaskLater(Runnable r, long delay) {
        return schedule(r, delay, 0, false);
    }

    public Task runTaskTimer(Runnable r, long delay, long period) {
        if (period < 1) {
            throw new IllegalArgumentException("Period must be positive");
        }
        return schedule(r, delay, period, false);
    }

    public Task runTaskAsync(Runnable r) {
        return schedule(r, 0, 0, true);
    }

    public Task runTaskLaterAsync(Runnable r, long delay) {
        return schedule(r, delay, 0, true);
    }

    public Task runTaskTimerAsync(Runnable r, long delay, long period) {
        if (period < 1) {
            throw new IllegalArgumentException("Period must be positive");
        }
        return schedule(r, delay, period, true);
    }

    public void cancelTasks() {
        async.cancelNativeTasks();
        Native.cancelTasks(owner);
    }
}
