package dev.zernyth.api.world;

import java.util.List;
import java.util.Optional;

import dev.zernyth.api.entity.Entity;
import dev.zernyth.api.player.Location;

/**
 * Native dimension view. Server-thread queries revalidate world identity. Reading an unloaded block returns
 * empty and never generates a chunk.
 */
public interface Dimension {

    /**
     * Returns the world containing this dimension.
     *
     * @return the world view
     */
    World getWorld();

    /**
     * Returns the native world or dimension identifier.
     *
     * @return the native identifier
     */
    String getIdentifier();

    /**
     * Checks current chunk availability without loading or generating it.
     *
     * @param chunkX the chunk X coordinate
     * @param chunkZ the chunk Z coordinate
     * @return whether the chunk is loaded now
     */
    boolean isChunkLoaded(int chunkX, int chunkZ);

    /**
     * Queries detached coordinates of currently loaded chunks.
     *
     * @return the detached loaded-chunk coordinates
     */
    List<ChunkPosition> getLoadedChunks();

    /**
     * Empty if not loaded. Never loads/generates a chunk. Native reads recheck loading.
     *
     * @param position the integer block position
     * @return the block view, or empty if its chunk is unloaded
     */
    Optional<Block> getBlock(BlockPosition position);

    /**
     * Attempts native spawning, returning empty on native spawn failure.
     *
     * @param location the destination including its dimension identifier
     * @param type the required class or native type identifier, according to the signature
     * @return the spawned entity, or empty if spawning failed
     */
    Optional<Entity> spawnEntity(Location location, String type);

    /**
     * Immutable chunk coordinates, distinct from block coordinates and safe to retain on workers.
     *
     * @param x the X coordinate
     * @param z the Z coordinate
     */
    record ChunkPosition(int x, int z) {

    }
}
