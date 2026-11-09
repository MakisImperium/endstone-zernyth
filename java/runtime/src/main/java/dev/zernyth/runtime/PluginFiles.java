package dev.zernyth.runtime;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import dev.zernyth.api.config.Configuration;
import dev.zernyth.api.plugin.PluginStorage;

final class PluginFiles implements PluginStorage, AutoCloseable {

    private final Path root;
    private final ClassLoader loader;
    private final Runnable checkThread;
    private final Map<String, Configuration> loaded = new HashMap<>();
    private boolean closed;

    PluginFiles(Path root, ClassLoader loader, Runnable checkThread) {
        this.root = root.toAbsolutePath().normalize();
        this.loader = loader;
        this.checkThread = checkThread;
    }

    private void check() {
        checkThread.run();
        if (closed) {
            throw new IllegalStateException("Plugin storage closed");
        }
    }

    static void noLinks(Path path) throws IOException {
        Path current = path.toAbsolutePath().getRoot();
        for (Path part : path.toAbsolutePath()) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                var attrs =
                        Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attrs.isSymbolicLink() || attrs.isOther()) {
                    throw new IOException("Links/reparse paths are not allowed");
                }
            }
        }
    }

    Path resolve(String name) throws IOException {
        if (name.isBlank() || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0) {
            throw new IOException("Invalid relative file path");
        }
        for (String segment : name.split("/", -1)) {
            if (!segment.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*") || segment.endsWith(".")
                    || segment.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) {
                throw new IOException("Invalid path segment");
            }
        }
        Path result = root.resolve(name).normalize();
        if (!result.startsWith(root)) {
            throw new IOException("Path escapes plugin directory");
        }
        noLinks(result);
        return result;
    }

    public Path getDataFolder() {
        check();
        return root;
    }

    private boolean json(String name) throws IOException {
        if (name.endsWith(".json")) {
            return true;
        }
        if (name.endsWith(".yml") || name.endsWith(".yaml")) {
            return false;
        }
        throw new IOException("Configuration must be YAML or JSON");
    }

    private Configuration read(String name) throws IOException {
        boolean json = json(name);
        Path file = resolve(name);
        if (!Files.exists(file)) {
            return new Configuration(Map.of());
        }
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(Codecs.LIMIT + 1);
            if (bytes.length > Codecs.LIMIT) {
                throw new IOException("Oversized configuration");
            }
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return new Configuration(Codecs.parse(text, json));
        }
    }

    public Configuration load(String name) throws IOException {
        check();
        if (!loaded.containsKey(name)) {
            loaded.put(name, read(name));
        }
        return loaded.get(name);
    }

    public void reload(String name) throws IOException {
        check();
        Configuration replacement = read(name);
        if (loaded.containsKey(name)) {
            loaded.get(name).replace(replacement.values());
        } else {
            loaded.put(name, replacement);
        }
    }

    private void write(Path destination, byte[] bytes) throws IOException {
        if (bytes.length > Codecs.LIMIT) {
            throw new IOException("Oversized configuration");
        }
        noLinks(destination);
        Files.createDirectories(destination.getParent());
        noLinks(destination);
        Path temporary = Files.createTempFile(destination.getParent(), ".zernyth-", ".tmp");
        try {
            Files.write(temporary, bytes);
            noLinks(destination);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void save(String name) throws IOException {
        check();
        write(resolve(name), Codecs.emit(load(name).values(), json(name)).getBytes(StandardCharsets.UTF_8));
    }

    public void saveDefault(String name) throws IOException {
        check();
        json(name);
        Path destination = resolve(name);
        if (Files.exists(destination)) {
            return;
        }
        try (var input = loader.getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Missing default configuration resource");
            }
            byte[] bytes = input.readNBytes(Codecs.LIMIT + 1);
            if (bytes.length > Codecs.LIMIT) {
                throw new IOException("Oversized default configuration");
            }
            Codecs.parse(Codecs.decode(bytes), json(name));
            write(destination, bytes);
        }
    }

    public void close() {
        closed = true;
        loaded.clear();
    }
}
