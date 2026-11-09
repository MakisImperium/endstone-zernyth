package dev.zernyth.api.text;

import java.util.List;
import java.util.Objects;

/**
 * Immutable Bedrock text values. No hover/click/RGB or Java Edition rendering promises.
 */
public sealed interface Component permits Component.Text, Component.Translation {

    /**
     * Immutable literal text component.
     *
     * @param content the literal text, non-null
     */
    record Text(String content) implements Component {

        /**
         * Requires non-null literal text; no native rendering or delivery happens during construction.
         *
         * @param content the literal text, non-null
         */
        public Text {
            Objects.requireNonNull(content);
        }
    }

    /**
     * Immutable translation key and copied arguments. Rendering depends on client translation resources.
     *
     * @param key the nonblank client translation key, at most 1024 characters
     * @param arguments at most 64 non-null translation arguments, defensively copied
     */
    record Translation(String key, List<String> arguments) implements Component {

        /**
         * Copies translation arguments and rejects blank/oversized keys or more than 64 arguments.
         *
         * @param key the nonblank client translation key, at most 1024 characters
         * @param arguments at most 64 non-null translation arguments, defensively copied
         */
        public Translation {
            Objects.requireNonNull(key);
            arguments = List.copyOf(arguments);
            if (key.isBlank() || key.length() > 1024 || arguments.size() > 64) {
                throw new IllegalArgumentException("Invalid translation");
            }
        }
    }

    /**
     * Creates an immutable literal text component.
     *
     * @param content the literal text, non-null
     * @return the literal component
     */
    static Text text(String content) {
        return new Text(content);
    }

    /**
     * Creates an immutable translation key component with copied arguments.
     *
     * @param key the nonblank client translation key, at most 1024 characters
     * @param arguments at most 64 non-null translation arguments, defensively copied
     * @return the translation component
     */
    static Translation translatable(String key, String... arguments) {
        return new Translation(key, List.of(arguments));
    }
}
