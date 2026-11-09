/**
 * Public Java 25 plugin API backed by the existing Endstone server.
 *
 * <p>Unless a member explicitly states otherwise, native operations require Endstone's
 * server thread and a live owning plugin generation. Expired handles, unavailable native
 * objects and wrong-thread native access fail with {@link java.lang.IllegalStateException}.
 * Required reference arguments are non-null unless documented as nullable; Java validation
 * uses {@link java.lang.NullPointerException} or {@link java.lang.IllegalArgumentException}.
 * A returned live view does not extend a native object's lifetime.
 *
 * <p>Immutable value objects may be read on workers. Native events and command sender views
 * have callback-scoped access. Never block the server thread waiting for a worker or a form
 * response. Java plugins are trusted in-process code, not a complete security sandbox.
 * Implementations may explicitly reject unsupported optional API operations.
 */
package dev.zernyth.api;
