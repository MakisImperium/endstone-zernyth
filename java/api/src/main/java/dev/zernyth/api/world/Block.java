package dev.zernyth.api.world;

import java.util.Map;
import java.util.Optional;

/**
 * Live block-position view. Native access rechecks chunk loading on the server thread; this view does not
 * keep a chunk loaded.
 */
public interface Block {

    /**
     * Returns the detached integer block position.
     *
     * @return the detached block position
     */
    BlockPosition getPosition();

    /**
     * Resolves a named dimension or this block's dimension; a lookup returns empty when absent.
     *
     * @return the dimension view, or an optional match for identifier lookups
     */
    Dimension getDimension();

    /**
     * Reads the native block data.
     *
     * @return the block data
     */
    BlockData getData();

    /**
     * Returns the represented item, block, entity or container type.
     *
     * @return the represented type
     */
    default String getType() {
        return getData().type();
    }

    /**
     * Replaces represented block data. Captured states are applied only by update.
     *
     * @param data the block data to apply or stage
     * @param applyPhysics whether to request native physics processing
     */
    void setData(BlockData data, boolean applyPhysics);

    /**
     * Sets a block identifier with an empty state map and physics disabled.
     *
     * @param type the required class or native type identifier, according to the signature
     */
    default void setType(String type) {
        setData(new BlockData(type, Map.of()), false);
    }

    /**
     * Resolves an offset block without loading its chunk, returning empty when unavailable.
     *
     * @param dx the X offset
     * @param dy the Y offset
     * @param dz the Z offset
     * @return the neighbor, or empty if its chunk is unavailable
     */
    Optional<Block> getRelative(int dx, int dy, int dz);

    /**
     * Captures plugin-owned native block state. Close it explicitly after use.
     *
     * @return a plugin-owned captured state that must be closed
     */
    BlockState captureState();
}
