package dev.zernyth.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

/**
 * No BDS/JNI simulation: verifies metadata rejection and isolated Java lifecycle only.
 */
public final class RuntimeTest {

    private static byte[] probeClass;

    private static void write(Path jar, String yaml) throws Exception {
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("plugin.yml"));
            out.write(yaml.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new JarEntry("fixture/Probe.class"));
            out.write(probeClass);
            out.closeEntry();
        }
    }

    public static void main(String[] args) throws Exception {
        Path jar = Files.createTempFile("zernyth-test", ".jar");
        Path directory = Files.createTempDirectory("zernyth-classes");
        try {
            Path source = directory.resolve("Probe.java");
            Files.writeString(source, """
                    package fixture;
                    public final class Probe extends dev.zernyth.api.plugin.JavaPlugin {
                        public void onLoad() {
                            System.setProperty("zernyth.test.state", "loaded");
                        }
                        public void onEnable() {
                            if (Boolean.getBoolean("zernyth.test.fail")) {
                                throw new IllegalStateException("expected");
                            }
                            if (!"loaded".equals(System.getProperty("zernyth.test.state"))) {
                                throw new AssertionError();
                            }
                            System.setProperty("zernyth.test.state", "enabled");
                        }
                        public void onDisable() {
                            System.setProperty("zernyth.test.state", "disabled");
                        }
                    }
                    """);
            int compiled = ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "25",
                    "-classpath", System.getProperty("java.class.path"), "-d", directory.toString(),
                    source.toString());
            if (compiled != 0) {
                throw new AssertionError("Fixture compilation failed");
            }
            probeClass = Files.readAllBytes(directory.resolve("fixture/Probe.class"));
            String yaml = "name: probe\nversion: 0.1.0\nmain: fixture.Probe\ndepend: [base]\n";
            write(jar, yaml);
            if (!Bootstrap.describe(jar.toString())[3].equals("base")) {
                throw new AssertionError();
            }
            try (var plugin = new Bootstrap(jar.toString(), 0)) {
                plugin.load();
                plugin.enable();
                plugin.disable();
            }
            if (!"disabled".equals(System.getProperty("zernyth.test.state"))) {
                throw new AssertionError();
            }
            System.setProperty("zernyth.test.fail", "true");
            try (var plugin = new Bootstrap(jar.toString(), 0)) {
                plugin.load();
                try {
                    plugin.enable();
                    throw new AssertionError("Expected activation failure");
                } catch (IllegalStateException expected) {
                }
                plugin.disable();
            } finally {
                System.clearProperty("zernyth.test.fail");
            }
            for (String invalid : new String[]{yaml.replace("probe", "Bad Name"), yaml + "name: duplicate\n",
                    yaml.replace("[base]", "base"), yaml.replace("version: 0.1.0\n", "")}) {
                write(jar, invalid);
                try {
                    Bootstrap.describe(jar.toString());
                    throw new AssertionError("Accepted invalid metadata");
                } catch (IOException expected) {
                }
            }
            write(jar, yaml.replace("fixture.Probe", "missing.Main"));
            try {
                new Bootstrap(jar.toString(), 0);
                throw new AssertionError("Expected missing main failure");
            } catch (ClassNotFoundException expected) {
            }
        } finally {
            Files.deleteIfExists(jar);
            try (var paths = Files.walk(directory)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
            System.clearProperty("zernyth.test.state");
        }
        System.out.println("Isolated Java lifecycle/metadata checks passed; BDS/JNI not tested.");
        AdvancedValueTest.run();
        NetworkValueTest.run();
        EcosystemValueTest.run();
        ManagedRuntimeTest.run();
        DeveloperApiTest.run();
        SecurityTest.run();
        DispatchQualityTest.run();
        ServerContractTest.run();
    }
}
