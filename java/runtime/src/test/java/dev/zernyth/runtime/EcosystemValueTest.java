package dev.zernyth.runtime;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import dev.zernyth.api.config.Configuration;
import dev.zernyth.api.plugin.JavaPlugin;

final class EcosystemValueTest {

    private static void check(boolean c) {
        if (!c) {
            throw new AssertionError("Ecosystem value check failed");
        }
    }

    private interface Action {

        void run() throws Exception;
    }

    private static void rejected(Action action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Unsafe input accepted");
        } catch (IOException | IllegalArgumentException expected) {
        }
    }

    private static void jar(Path file, String resource) throws Exception {
        try (var out = new JarOutputStream(Files.newOutputStream(file))) {
            if (resource != null) {
                out.putNextEntry(new JarEntry(resource));
                try (var in = EcosystemValueTest.class.getClassLoader().getResourceAsStream(resource)) {
                    in.transferTo(out);
                }
                out.closeEntry();
            }
        }
    }

    static void run() throws Exception {
        Configuration config = new Configuration(
                Codecs.parse("server:\n  name: test\n  count: 42\nitems: [one, two]\n", false));
        check(config.getInt("server.count", 0) == 42 && config.getList("items", String.class).size() == 2);
        rejected(() -> config.getInt("server.name", 0));
        rejected(() -> Codecs.parse("a: 1\na: 2", false));
        rejected(() -> Codecs.parse("a: !!java/object:java.lang.Runtime {}", false));
        rejected(() -> Codecs.parse("a: &a [1]\nb: *a", false));
        rejected(() -> Codecs.parse("{broken", true));
        Path base = Path.of("build").toAbsolutePath();
        Files.createDirectories(base);
        Path directory = Files.createTempDirectory(base, "ecosystem-values-");
        try {
            try (var storage = new PluginFiles(directory, JavaPlugin.class.getClassLoader(), () -> {
            })) {
                rejected(() -> storage.load("../outside.yml"));
                rejected(() -> storage.load("C:/outside.yml"));
                rejected(() -> storage.load("NUL.yml"));
                Configuration json = storage.load("nested/data.json");
                json.set("count", 42);
                storage.save("nested/data.json");
                json.set("count", 0);
                storage.reload("nested/data.json");
                check(json.getInt("count", 0) == 42);
                Files.writeString(directory.resolve("nested/data.json"), "{broken", StandardCharsets.UTF_8);
                rejected(() -> storage.reload("nested/data.json"));
                check(json.getInt("count", 0) == 42);
                Files.write(directory.resolve("huge.yml"), new byte[Codecs.LIMIT + 1]);
                rejected(() -> storage.load("huge.yml"));
            }
            String greeting = "dev/zernyth/fixture/shared/Greeting.class";
            Path first = directory.resolve("first.jar"), second = directory.resolve("second.jar"),
                    empty = directory.resolve("empty.jar"), core = directory.resolve("core.jar");
            jar(first, greeting);
            jar(second, greeting);
            jar(empty, null);
            jar(core, "dev/zernyth/api/plugin/JavaPlugin.class");
            long openBefore = LoaderTracker.snapshot().openPluginLoaders();
            var weakLoaders = new ArrayList<WeakReference<ClassLoader>>();
            for (int generation = 0; generation < 20; generation++) {
                try (var loader = new PluginClassLoader(empty, List.of(), List.of(), List.of())) {
                    weakLoaders.add(new WeakReference<>(loader));
                }
            }
            check(LoaderTracker.snapshot().openPluginLoaders() == openBefore);
            System.gc(); // Observation only: collection is never a correctness assertion.
            System.out.println("Closed loader weak-reference observation: "
                    + weakLoaders.stream().filter(r -> r.get() != null).count()
                    + "/20 still reachable; GC nondeterministic.");
            try (var a = new PluginClassLoader(
                    first, List.of(), List.of(), List.of("dev.zernyth.fixture.shared"));
                 var b = new PluginClassLoader(second, List.of(), List.of(), List.of());
                 var child = new PluginClassLoader(empty, List.of(), List.of(a), List.of())) {
                String name = "dev.zernyth.fixture.shared.Greeting";
                check(a.loadClass(name) != b.loadClass(name));
                check(child.loadClass(name) == a.loadClass(name));
                check(a.loadClass(JavaPlugin.class.getName()) == JavaPlugin.class);
                rejected(() -> new PluginClassLoader(first, List.of(second), List.of(), List.of()));
                rejected(() -> new PluginClassLoader(core, List.of(), List.of(), List.of()));
                try (var library = new PluginClassLoader(empty, List.of(second), List.of(), List.of())) {
                    check(library.libraries.size() == 1 && library.loadClass(name) != a.loadClass(name));
                }
            }
        } finally {
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        System.out.println("Ecosystem configuration/path/class-loader/library checks passed (no BDS/JNI).");
    }
}
