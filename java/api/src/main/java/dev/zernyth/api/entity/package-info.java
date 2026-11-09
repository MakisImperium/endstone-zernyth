/**
 * Entity native operations require the server thread and a live owning plugin.
 * Removal, runtime-ID change or loss of the world invalidates a view permanently.
 * isValid returns false for a retired entity; other native operations throw
 * IllegalStateException. Disabled plugins and wrong threads always fail explicitly.
 * Actor IDs and runtime IDs are stable native identifiers, not pointers or UUIDs;
 * runtime IDs use Java long as a 64-bit bit pattern. Velocity is a value snapshot.
 * Health is available for native Mob actors only. Player removal is rejected; use kick.
 */
package dev.zernyth.api.entity;
