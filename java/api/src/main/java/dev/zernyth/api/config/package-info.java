/**
 * Java 25 configuration values. Configuration is synchronized Java-only state;
 * returned maps/lists are detached from it. Invalid types, paths and values throw
 * IllegalArgumentException/NullPointerException. PluginStorage performs bounded
 * UTF-8 file I/O on the lifecycle/server thread and throws IOException for invalid
 * documents, paths or file failures; closed/wrong-thread access throws IllegalStateException.
 * Reload replaces values only after complete validation. No implicit disk save.
 */
package dev.zernyth.api.config;
