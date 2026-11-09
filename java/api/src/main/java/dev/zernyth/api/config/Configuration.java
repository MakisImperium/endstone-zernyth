package dev.zernyth.api.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Java-only configuration values. Synchronized mutations; reads return detached values. No implicit disk
 * writes.
 */
public final class Configuration {

    private Map<String, Object> values;

    private static String text(String value) {
        if (value.length() > 1024 * 1024) {
            throw new IllegalArgumentException("Configuration string limit");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) {
                    throw new IllegalArgumentException("Invalid Unicode");
                }
            } else if (Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException("Invalid Unicode");
            }
        }
        return value;
    }

    /**
     * Validates and detaches the supplied in-memory configuration tree; this does not read or write files.
     *
     * @param values the detached values; collection inputs are copied where specified
     */
    public Configuration(Map<String, ?> values) {
        replace(values);
    }

    /**
     * Validates a complete replacement tree before changing in-memory configuration; disk remains unchanged.
     *
     * @param input the replacement configuration tree
     */
    public synchronized void replace(Map<String, ?> input) {
        values = copyMap(input, 0);
    }

    /**
     * Copies all configuration values into a detached unmodifiable map.
     *
     * @return an unmodifiable detached map
     */
    public synchronized Map<String, Object> values() {
        return Collections.unmodifiableMap(copyMap(values, 0));
    }

    private static Map<String, Object> copyMap(Map<?, ?> input, int depth) {
        return copyMap(input, depth, new int[]{100000});
    }

    private static Map<String, Object> copyMap(Map<?, ?> input, int depth, int[] budget) {
        if (depth > 50 || input.size() > 10000) {
            throw new IllegalArgumentException("Configuration structure too large");
        }
        var result = new LinkedHashMap<String, Object>();
        input.forEach((k, v) -> {
            if (!(k instanceof String key)) {
                throw new IllegalArgumentException("Configuration keys must be strings");
            }
            result.put(text(key), copy(v, depth + 1, budget));
        });
        return result;
    }

    private static Object copy(Object value, int depth) {
        return copy(value, depth, new int[]{100000});
    }

    private static Object copy(Object value, int depth, int[] budget) {
        if (--budget[0] < 0) {
            throw new IllegalArgumentException("Configuration node limit");
        }
        if (depth > 50) {
            throw new IllegalArgumentException("Configuration nesting limit");
        }
        if (value instanceof String string) {
            return text(string);
        }
        if (value == null || value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            return value;
        }
        if (value instanceof Double d && Double.isFinite(d)) {
            return d;
        }
        if (value instanceof Float f && Float.isFinite(f)) {
            return f.doubleValue();
        }
        if (value instanceof Map<?, ?> m) {
            return copyMap(m, depth, budget);
        }
        if (value instanceof List<?> list) {
            if (list.size() > 10000) {
                throw new IllegalArgumentException("Configuration list too large");
            }
            var result = new ArrayList<Object>();
            for (var item : list) {
                result.add(copy(item, depth + 1, budget));
            }
            return Collections.unmodifiableList(result);
        }
        throw new IllegalArgumentException("Unsupported configuration value type");
    }

    private static String[] keys(String path) {
        Objects.requireNonNull(path);
        if (path.length() > 4096 || path.chars().filter(c -> c == '.').count() >= 50) {
            throw new IllegalArgumentException("Configuration path limit");
        }
        if (!path.matches("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*")) {
            throw new IllegalArgumentException("Invalid configuration path");
        }
        return path.split("\\.");
    }

    /**
     * Reads a configuration path or typed command argument. Configuration returns null for missing/null
     * values.
     *
     * @param path the validated dot-separated configuration path
     * @return the requested value; missing configuration paths return null
     */
    public synchronized Object get(String path) {
        Object current = values;
        for (String key : keys(path)) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }
        return copy(current, 0);
    }

    /**
     * Validates a candidate tree before setting a dotted path; failure preserves existing values and disk is
     * unchanged.
     *
     * @param path the validated dot-separated configuration path
     * @param value the replacement value; null is permitted only where the enclosing contract specifies
     */
    public synchronized void set(String path, Object value) {
        String[] keys = keys(path);
        if (keys.length > 50) {
            throw new IllegalArgumentException("Configuration nesting limit");
        }
        Object replacement = copy(value, 0);
        Map<String, Object> candidate = copyMap(values, 0), current = candidate;
        for (int i = 0; i < keys.length - 1; i++) {
            Object child = current.get(keys[i]);
            if (child == null) {
                var next = new LinkedHashMap<String, Object>();
                current.put(keys[i], next);
                current = next;
            } else if (child instanceof Map<?, ?> map) {
                var next = copyMap(map, 0);
                current.put(keys[i], next);
                current = next;
            } else {
                throw new IllegalArgumentException("Non-section configuration path");
            }
        }
        current.put(keys[keys.length - 1], replacement);
        values = copyMap(candidate, 0);
    }

    private <T> T typed(String path, T fallback, Class<T> type) {
        Object value = get(path);
        if (value == null) {
            return fallback;
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Wrong type at " + path);
        }
        return type.cast(value);
    }

    /**
     * Reads a string or fallback for missing/null values; wrong stored types are rejected.
     *
     * @param path the validated dot-separated configuration path
     * @param fallback the value returned for a missing/null configuration entry
     * @return the string value or fallback
     */
    public String getString(String path, String fallback) {
        return typed(path, fallback, String.class);
    }

    /**
     * Reads a boolean or fallback for missing/null values; wrong stored types are rejected.
     *
     * @param path the validated dot-separated configuration path
     * @param fallback the value returned for a missing/null configuration entry
     * @return the boolean value or fallback
     */
    public boolean getBoolean(String path, boolean fallback) {
        return typed(path, fallback, Boolean.class);
    }

    /**
     * Reads an exactly integral in-range number, or fallback for missing/null values.
     *
     * @param path the validated dot-separated configuration path
     * @param fallback the value returned for a missing/null configuration entry
     * @return the in-range integer or fallback
     */
    public int getInt(String path, int fallback) {
        Object value = get(path);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())
                || n.doubleValue() != Math.rint(n.doubleValue()) || n.doubleValue() < Integer.MIN_VALUE
                || n.doubleValue() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Expected integer at " + path);
        }
        return n.intValue();
    }

    /**
     * Reads a finite number, or fallback for missing/null values.
     *
     * @param path the validated dot-separated configuration path
     * @param fallback the value returned for a missing/null configuration entry
     * @return the finite number or fallback
     */
    public double getDouble(String path, double fallback) {
        Object value = get(path);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) {
            throw new IllegalArgumentException("Expected number at " + path);
        }
        return n.doubleValue();
    }

    /**
     * Reads a detached typed list; missing/null values produce an empty list and wrong element types are
     * rejected.
     *
     * @param path the validated dot-separated configuration path
     * @param type the required class or native type identifier, according to the signature
     * @param <T> the argument or shared service value type
     * @return an unmodifiable typed list, empty for missing/null values
     */
    public <T> List<T> getList(String path, Class<T> type) {
        Object value = get(path);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected list at " + path);
        }
        var result = new ArrayList<T>();
        for (var item : list) {
            if (!type.isInstance(item)) {
                throw new IllegalArgumentException("Wrong list type at " + path);
            }
            result.add(type.cast(item));
        }
        return List.copyOf(result);
    }
}
