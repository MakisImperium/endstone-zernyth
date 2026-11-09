package dev.zernyth.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import javax.tools.ToolProvider;

import dev.zernyth.api.Server;
import dev.zernyth.api.player.Player;

/**
 * Checks productive overrides and a synthetic previously compiled Server implementation without JNI.
 */
final class ServerContractTest {

    private ServerContractTest() {
    }

    static void run() throws Exception {
        int compatibilityDefaults = 0;
        for (Method method : Server.class.getDeclaredMethods()) {
            if (method.isDefault()) {
                Method production = GameServer.class.getMethod(method.getName(), method.getParameterTypes());
                if (production.getDeclaringClass() != GameServer.class) {
                    throw new AssertionError("Production inherited a compatibility fallback: " + method);
                }
                compatibilityDefaults++;
            }
        }
        if (compatibilityDefaults != 6) {
            throw new AssertionError("Review changes to the Server compatibility surface");
        }
        Class<?> playerImplementation = null;
        for (Class<?> nested : GameServer.class.getDeclaredClasses()) {
            if (Player.class.isAssignableFrom(nested)) {
                playerImplementation = nested;
            }
        }
        if (playerImplementation == null) {
            throw new AssertionError("Missing production player implementation");
        }
        for (Method method : Player.class.getDeclaredMethods()) {
            if (method.isDefault() &&
                    playerImplementation.getMethod(method.getName(), method.getParameterTypes())
                            .getDeclaringClass().isInterface()) {
                throw new AssertionError("Production player inherited a fallback: " + method);
            }
        }
        legacyBinary();
        System.out.println("Server production overrides and legacy linkage passed "
                + "(Java-only test; no JNI/BDS).");
    }

    private static void legacyBinary() throws Exception {
        Path build = Path.of("build").toAbsolutePath().normalize();
        Files.createDirectories(build);
        Path directory = Files.createTempDirectory(build, "legacy-server-");
        try {
            Path api = directory.resolve("src/dev/zernyth/api/Server.java");
            Path implementation = directory.resolve("src/fixture/LegacyServer.java");
            Files.createDirectories(api.getParent());
            Files.createDirectories(implementation.getParent());
            Files.writeString(api, """
                    package dev.zernyth.api;
                    import java.util.List;
                    import dev.zernyth.api.command.Command;
                    import dev.zernyth.api.command.CommandExecutor;
                    import dev.zernyth.api.player.Player;
                    import dev.zernyth.api.plugin.PluginManager;
                    import dev.zernyth.api.scheduler.Scheduler;
                    public interface Server {
                        List<Player> getOnlinePlayers();
                        PluginManager getPluginManager();
                        Scheduler getScheduler();
                        Command registerCommand(String name, String description,
                                String permission, CommandExecutor executor);
                    }
                    """);
            Files.writeString(implementation, """
                    package fixture;
                    import java.util.List;
                    import dev.zernyth.api.Server;
                    import dev.zernyth.api.command.Command;
                    import dev.zernyth.api.command.CommandExecutor;
                    import dev.zernyth.api.player.Player;
                    import dev.zernyth.api.plugin.PluginManager;
                    import dev.zernyth.api.scheduler.Scheduler;
                    public final class LegacyServer implements Server {
                        public List<Player> getOnlinePlayers() {
                            return List.of();
                        }
                        public PluginManager getPluginManager() {
                            return null;
                        }
                        public Scheduler getScheduler() {
                            return null;
                        }
                        public Command registerCommand(String name, String description,
                                String permission, CommandExecutor executor) {
                            return null;
                        }
                    }
                    """);
            Path classes = directory.resolve("classes");
            int exit = ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "25",
                    "-classpath", System.getProperty("java.class.path"), "-d", classes.toString(),
                    api.toString(), implementation.toString());
            if (exit != 0) {
                throw new AssertionError("Legacy fixture compilation failed");
            }
            try (var loader = new URLClassLoader(new URL[]{classes.toUri().toURL()},
                    Server.class.getClassLoader())) {
                Server legacy = (Server) loader.loadClass("fixture.LegacyServer").getConstructor()
                        .newInstance();
                for (Method method : Server.class.getDeclaredMethods()) {
                    if (!method.isDefault()) {
                        continue;
                    }
                    Object[] arguments = method.getParameterCount() == 0
                            ? new Object[0] : new Object[]{"minecraft:stone", 1};
                    try {
                        method.invoke(legacy, arguments);
                        throw new AssertionError("Expected explicit compatibility rejection: " + method);
                    } catch (InvocationTargetException expected) {
                        if (!(expected.getCause() instanceof UnsupportedOperationException)) {
                            throw new AssertionError("Legacy implementation lost default linkage", expected);
                        }
                    }
                }
            }
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }
}
