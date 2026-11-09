package dev.zernyth.runtime;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import dev.zernyth.api.plugin.JavaPlugin;

/**
 * Own classes, explicit exported dependency classes, then JDK. Core API always delegates to stable parent.
 */
final class PluginClassLoader extends URLClassLoader {

    final Set<String> classes = new HashSet<>();
    final List<String> libraries = new ArrayList<>();
    final List<PluginClassLoader> imports;
    final List<String> exports;
    private boolean closed;

    static boolean protectedClass(String name) {
        return List
                .of("java.", "javax.", "jdk.", "sun.", "com.sun.", "dev.zernyth.api.", "dev.zernyth.runtime.")
                .stream()
                .anyMatch(name::startsWith);
    }

    PluginClassLoader(Path main, List<Path> jars, List<PluginClassLoader> imports, List<String> exports)
            throws IOException {
        super(urls(main, jars), JavaPlugin.class.getClassLoader());
        this.imports = List.copyOf(imports);
        this.exports = List.copyOf(exports);
        try {
            scan(main);
            for (Path jar : jars) {
                scan(jar);
                try (var input = Files.newInputStream(jar)) {
                    var digest = MessageDigest.getInstance("SHA-256");
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    for (int n; (n = input.read(buffer)) != -1; ) {
                        if ((total += n) > 128L * 1024 * 1024) {
                            throw new IOException("Library size changed beyond limit");
                        }
                        digest.update(buffer, 0, n);
                    }
                    libraries.add(jar.getFileName() + " sha256=" + HexFormat.of().formatHex(digest.digest()));
                } catch (NoSuchAlgorithmException impossible) {
                    throw new AssertionError(impossible);
                }
            }
            var imported = new HashMap<String, PluginClassLoader>();
            for (var dependency : imports) {
                for (String name : dependency.classes) {
                    if (dependency.exported(name)) {
                        if (classes.contains(name) || imported.putIfAbsent(name, dependency) != null) {
                            throw new IOException("Conflicting exported class: " + name);
                        }
                    }
                }
            }
            LoaderTracker.created(this);
        } catch (Throwable error) {
            super.close();
            throw error;
        }
    }

    private static URL[] urls(Path main, List<Path> jars) throws IOException {
        var paths = new ArrayList<Path>();
        paths.add(main);
        paths.addAll(jars);
        long size = 0;
        var urls = new ArrayList<URL>();
        for (Path path : paths) {
            PluginFiles.noLinks(path);
            if (!Files.isRegularFile(path) || (size += Files.size(path)) > 128L * 1024 * 1024) {
                throw new IOException("Missing/oversized plugin JARs");
            }
            urls.add(path.toUri().toURL());
        }
        return urls.toArray(URL[]::new);
    }

    private void scan(Path path) throws IOException {
        var inJar = new HashSet<String>();
        var entriesSeen = new HashSet<String>();
        try (var jar = new JarFile(path.toFile())) {
            if (jar.getManifest() != null
                    && jar.getManifest().getMainAttributes().getValue("Class-Path") != null) {
                throw new IOException("Implicit JAR Class-Path is forbidden");
            }
            long expanded = 0;
            int entries = 0;
            for (var entry : Collections.list(jar.entries())) {
                if (++entries > 50000 || entry.getSize() < 0
                        || (expanded += entry.getSize()) > 128L * 1024 * 1024) {
                    throw new IOException("JAR expansion limit");
                }
                String name = entry.getName();
                if (!entriesSeen.add(name) || name.startsWith("/") || name.indexOf('\\') >= 0
                        || Arrays.asList(name.split("/")).contains("..")) {
                    throw new IOException("Invalid/duplicate JAR entry");
                }
                if (name.equals("META-INF/INDEX.LIST")) {
                    throw new IOException("Implicit JAR index is forbidden");
                }
                if (!name.endsWith(".class")) {
                    continue;
                }
                int version = 0;
                if (name.startsWith("META-INF/versions/")) {
                    var parts = name.split("/", 4);
                    try {
                        version = Integer.parseInt(parts[2]);
                    } catch (RuntimeException error) {
                        throw new IOException("Invalid multi-release class", error);
                    }
                    name = parts[3];
                }
                String binary = name.substring(0, name.length() - 6).replace('/', '.');
                if (binary.equals("module-info")) {
                    continue;
                }
                if (protectedClass(binary)) {
                    throw new IOException("Bundled core class is forbidden: " + binary);
                }
                if (version > 25) {
                    continue;
                }
                try (var stream = new DataInputStream(jar.getInputStream(entry))) {
                    if (stream.readInt() != 0xcafebabe || stream.readUnsignedShort() == 65535
                            || stream.readUnsignedShort() > 69) {
                        throw new IOException("Invalid/unsupported class version");
                    }
                }
                inJar.add(binary);
            }
        }
        for (String name : inJar) {
            if (!classes.add(name)) {
                throw new IOException("Duplicate class across plugin/libraries: " + name);
            }
        }
        if (classes.size() > 10000) {
            throw new IOException("Plugin class limit exceeded");
        }
    }

    boolean exported(String name) {
        return exports.stream().anyMatch(p -> name.startsWith(p + "."));
    }

    private Class<?> exportedClass(String name) throws ClassNotFoundException {
        if (closed || !exported(name) || !classes.contains(name)) {
            throw new ClassNotFoundException(name);
        }
        return loadClass(name);
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> result = findLoadedClass(name);
        if (result == null) {
            if (protectedClass(name)) {
                result = getParent().loadClass(name);
            } else {
                if (closed) {
                    throw new ClassNotFoundException("Plugin class loader closed: " + name);
                }
                if (classes.contains(name)) {
                    result = findClass(name);
                } else {
                    for (var dependency : imports) {
                        if (dependency.exported(name) && dependency.classes.contains(name)) {
                            result = dependency.exportedClass(name);
                            break;
                        }
                    }
                    if (result == null) {
                        result = ClassLoader.getPlatformClassLoader().loadClass(name);
                    }
                }
            }
        }
        if (resolve) {
            resolveClass(result);
        }
        return result;
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            super.close();
        } finally {
            LoaderTracker.closed(this);
        }
    }
}
