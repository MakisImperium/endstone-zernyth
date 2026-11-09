package dev.zernyth.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import dev.zernyth.api.config.Configuration;

final class SecurityTest {

    interface Action {

        void run() throws Exception;
    }

    static void rejected(Action action) throws Exception {
        try {
            action.run();
        } catch (IOException | IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Invalid input accepted");
    }

    static void run() throws Exception {
        rejected(() -> Codecs.parse("{\"x\":\"" + (char) 0xd800 + "\"}", true));
        rejected(() -> Codecs.parse("{\"x\":\"\\ud800\"}", true));
        rejected(() -> Codecs.parse("{\"x\":null,\"x\":1}", true));
        rejected(() -> Codecs.parse("{\"nested\":{\"x\":1,\"x\":2}}", true));
        var configuration = new Configuration(Map.of("stable", 1));
        var before = configuration.values();
        rejected(() -> configuration.set("created.section.value", new Object()));
        if (!configuration.values().equals(before)) {
            throw new AssertionError("Failed mutation changed configuration");
        }
        rejected(() -> configuration.set(String.join(".", Collections.nCopies(55, "deep")), 1));
        rejected(() -> configuration.get(String.join(".", Collections.nCopies(10000, "a"))));
        if (!configuration.values().equals(before)) {
            throw new AssertionError("Deep mutation changed configuration");
        }
        var expanded = new LinkedHashMap<String, Object>();
        for (int i = 0; i < 100; i++) {
            expanded.put("k" + i, Collections.nCopies(2000, 1));
        }
        rejected(() -> new Configuration(expanded));
        var largeOutput = Map.<String, Object>of("items", Collections.nCopies(100, "x".repeat(20000)));
        rejected(() -> Codecs.emit(largeOutput, true));
        rejected(() -> Codecs.emit(largeOutput, false));
        Path directory = Path.of("build/security-fuzz").toAbsolutePath();
        Files.createDirectories(directory);
        Path jar = Files.createTempFile(directory, "synthetic-", ".jar");
        try {
            try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
                out.putNextEntry(new JarEntry("plugin.yml"));
                out.write("name: valid\nversion: 1\nmain: fixture.Probe\n# ".getBytes(
                        StandardCharsets.UTF_8));
                out.write(new byte[]{(byte) 0xc3, 0x28});
                out.closeEntry();
            }
            rejected(() -> Manifest.read(jar));
            var random = new Random(1208);
            for (int i = 0; i < 300; i++) {
                byte[] bytes = new byte[random.nextInt(512) + 1];
                random.nextBytes(bytes);
                Files.write(jar, bytes);
                rejected(() -> Manifest.read(jar));
                String input = new String(bytes, StandardCharsets.ISO_8859_1);
                try {
                    Codecs.parse(input, true);
                } catch (IOException expected) {
                }
                try {
                    Codecs.parse(input, false);
                } catch (IOException expected) {
                }
            }
        } finally {
            Files.deleteIfExists(jar);
        }
        System.out.println(
                "Security regressions and 300 seeded bounded parser/JAR fuzz cases passed; no JAR executed.");
    }
}
