/**
 * Zernyth native API: Java 25 LTS minimum; Eclipse Temurin 25 preferred.
 * Native operations require Endstone's current server thread and a live owning plugin;
 * violations, expired handles and unavailable native objects throw IllegalStateException.
 * Null required arguments throw NullPointerException; invalid Java arguments throw
 * IllegalArgumentException. Immutable value snapshots may be read on any thread.
 * Event cancellation and command callbacks are synchronous. Async tasks may use
 * Java-only state and the plugin logger, but no native gameplay operations.
 */
package dev.zernyth.api.command;
