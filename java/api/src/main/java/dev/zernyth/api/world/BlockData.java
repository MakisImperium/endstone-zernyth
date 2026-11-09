package dev.zernyth.api.world;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable block identifier and scalar state map. Java validation does not prove that the native registry
 * supports a particular combination.
 *
 * @param type the required class or native type identifier, according to the signature
 * @param states the copied native scalar block-state map
 */
public record BlockData(String type, Map<String, Object> states) {

    /**
     * Copies scalar state values and validates identifier syntax. Registry compatibility is checked only when
     * the value is used natively.
     *
     * @param type the required class or native type identifier, according to the signature
     * @param states the copied native scalar block-state map
     */
    public BlockData {
        Objects.requireNonNull(type);
        states = Map.copyOf(states);
        if (!type.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid block identifier");
        }
        for (Object v : states.values()) {
            if (!(v instanceof String || v instanceof Integer || v instanceof Boolean)) {
                throw new IllegalArgumentException("Invalid block state type");
            }
        }
    }
}
