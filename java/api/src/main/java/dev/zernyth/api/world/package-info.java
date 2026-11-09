/**
 * World, dimension, block and captured-state native operations require the server thread
 * and a live owning plugin. Native failures, unloaded chunks and stale world/state
 * references throw IllegalStateException. getBlock returns Optional.empty for an
 * unloaded chunk; later reads and writes recheck loading and never load or generate
 * chunks. Identifiers and positions cached in views are values, not validity checks.
 * BlockData and BlockPosition are immutable and worker-safe; invalid values fail with
 * IllegalArgumentException, NullPointerException or ArithmeticException (offset overflow).
 * Captured BlockState objects must be closed, preferably with try-with-resources;
 * plugin disable also releases them. Updating a state follows native force/physics semantics.
 */
package dev.zernyth.api.world;
