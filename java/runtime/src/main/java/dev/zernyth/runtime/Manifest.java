package dev.zernyth.runtime;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

record Manifest(String name, String version, String main, List<String> depend, List<String> soft,
                List<String> before, List<String> libraries, List<String> exports, List<String> imports,
                Map<String, Object> commands) {

    static Manifest read(Path path) throws IOException {
        Map<String, Object> values;
        try (var jar = new JarFile(path.toFile())) {
            var entry = jar.getJarEntry("plugin.yml");
            if (entry == null || entry.getSize() > 65536) {
                throw new IOException("Missing/oversized plugin.yml");
            }
            try (var input = jar.getInputStream(entry)) {
                byte[] bytes = input.readNBytes(65537);
                if (bytes.length > 65536) {
                    throw new IOException("Oversized plugin.yml");
                }
                values = Codecs.parse(Codecs.decode(bytes), false);
            }
        }
        for (String key : values.keySet()) {
            if (!Set.of("name", "version", "main", "depend", "softdepend", "loadbefore", "libraries",
                            "exports", "imports", "commands")
                    .contains(key)) {
                throw new IOException("Unsupported metadata key: " + key);
            }
        }
        String name = string(values.get("name")),
                version = values.get("version") instanceof Number number ? number.toString()
                        : string(values.get("version")),
                main = string(values.get("main"));
        if (!name.matches("[a-z0-9_]+") || name.startsWith("endstone")
                || Set.of("minecraft", "mojang").contains(name)) {
            throw new IOException("Invalid plugin name");
        }
        if (!version.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]*")) {
            throw new IOException("Invalid plugin version");
        }
        if (!main.matches("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)+")) {
            throw new IOException("Invalid main class");
        }
        List<String> depend = list(values, "depend"), soft = list(values, "softdepend"),
                before = list(values, "loadbefore");
        for (var dependencies : List.of(depend, soft, before)) {
            for (String item : dependencies) {
                if (!item.matches("[a-z0-9_]+")) {
                    throw new IOException("Invalid dependency name");
                }
            }
        }
        var imports = list(values, "imports");
        for (String item : imports) {
            if (!depend.contains(item) && !soft.contains(item)) {
                throw new IOException("Imports must name declared dependencies");
            }
        }
        var exports = list(values, "exports");
        for (String item : exports) {
            if (!item.matches("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)+")
                    || PluginClassLoader.protectedClass(item + ".")) {
                throw new IOException("Invalid exported package");
            }
        }
        var libraries = list(values, "libraries");
        if (libraries.size() > 16) {
            throw new IOException("Too many local libraries");
        }
        for (String item : libraries) {
            if (!item.matches("[A-Za-z0-9][A-Za-z0-9_.-]*\\.jar")) {
                throw new IOException("Libraries must be local JAR filenames");
            }
        }
        Map<String, Object> commands = Map.of();
        if (values.containsKey("commands")) {
            if (!(values.get("commands") instanceof Map<?, ?> map)) {
                throw new IOException("commands must be a mapping");
            }
            commands = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                String key = string(entry.getKey());
                if (!key.matches("[a-z][a-z0-9_]*") || !(entry.getValue() instanceof Map<?, ?>)) {
                    throw new IOException("Invalid command declaration");
                }
                commands.put(key, entry.getValue());
            }
        }
        return new Manifest(
                name, version, main, depend, soft, before, libraries, exports, imports, Map.copyOf(commands));
    }

    static String string(Object value) throws IOException {
        if (!(value instanceof String text)) {
            throw new IOException("Expected metadata string");
        }
        return text;
    }

    static List<String> list(Map<String, Object> values, String name) throws IOException {
        Object value = values.get(name);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list) || list.size() > 128) {
            throw new IOException("Expected bounded list: " + name);
        }
        var result = new ArrayList<String>();
        for (var item : list) {
            String text = string(item);
            if (result.contains(text)) {
                throw new IOException("Duplicate list entry");
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    String[] describe() {
        return new String[]{name, version, main, String.join(",", depend), String.join(",", soft),
                String.join(",", before)};
    }
}
