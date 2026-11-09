package dev.zernyth.runtime;

import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import com.google.gson.ToNumberPolicy;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.zernyth.api.config.Configuration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

final class Codecs {

    static final int LIMIT = 1024 * 1024;

    private static Yaml yaml() {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(50);
        options.setCodePointLimit(LIMIT);
        return new Yaml(new SafeConstructor(options));
    }

    private static final Gson JSON = new GsonBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .setPrettyPrinting()
            .create();

    static String decode(byte[] bytes) throws IOException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static void uniqueKeys(JsonReader reader, int depth) throws IOException {
        if (depth > 50) {
            throw new IOException("JSON nesting limit");
        }
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                var names = new HashSet<String>();
                while (reader.hasNext()) {
                    if (!names.add(reader.nextName())) {
                        throw new IOException("Duplicate JSON property");
                    }
                    uniqueKeys(reader, depth + 1);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) {
                    uniqueKeys(reader, depth + 1);
                }
                reader.endArray();
            }
            default -> reader.skipValue();
        }
    }

    static Map<String, Object> parse(String text, boolean json) throws IOException {
        if (text.length() > LIMIT) {
            throw new IOException("Document exceeds character limit");
        }
        StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(text));
        if (text.getBytes(StandardCharsets.UTF_8).length > LIMIT) {
            throw new IOException("Document exceeds 1 MiB");
        }
        try {
            Object value;
            if (json) {
                var validation = new JsonReader(new StringReader(text));
                validation.setStrictness(Strictness.STRICT);
                validation.setNestingLimit(50);
                uniqueKeys(validation, 0);
                var reader = new JsonReader(new StringReader(text));
                reader.setStrictness(Strictness.STRICT);
                reader.setNestingLimit(50);
                value = JSON.fromJson(reader, Map.class);
                if (reader.peek() != JsonToken.END_DOCUMENT) {
                    throw new IOException("Trailing JSON content");
                }
            } else {
                value = yaml().load(text);
            }
            if (value == null) {
                return new LinkedHashMap<>();
            }
            if (!(value instanceof Map<?, ?> map)) {
                throw new IOException("Expected object at document root");
            }
            var normalized = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IOException("Expected string property");
                }
                normalized.put(key, entry.getValue());
            }
            return new Configuration(normalized).values();
        } catch (RuntimeException error) {
            throw new IOException("Invalid " + (json ? "JSON" : "YAML") + " document ("
                    + error.getClass().getSimpleName() + ")");
        }
    }

    static String emit(Map<String, Object> values, boolean json) throws IOException {
        // Bound serialization while it happens, rather than after an arbitrarily large String allocation.
        var destination = new Writer() {
            final StringBuilder buffer = new StringBuilder();

            public void write(char[] data, int offset, int length) throws IOException {
                Objects.checkFromIndexSize(offset, length, data.length);
                if (length > LIMIT - buffer.length()) {
                    throw new IOException("Document serialization limit");
                }
                buffer.append(data, offset, length);
            }

            public void flush() {
            }

            public void close() {
            }

            public String toString() {
                return buffer.toString();
            }
        };
        try {
            if (json) {
                JSON.toJson(values, destination);
            } else {
                yaml().dump(values, destination);
            }
        } catch (RuntimeException error) {
            throw new IOException(
                    "Configuration serialization rejected (" + error.getClass().getSimpleName() + ")");
        }
        String output = destination.toString();
        if (output.getBytes(StandardCharsets.UTF_8).length > LIMIT) {
            throw new IOException("Document exceeds 1 MiB");
        }
        return output;
    }
}
