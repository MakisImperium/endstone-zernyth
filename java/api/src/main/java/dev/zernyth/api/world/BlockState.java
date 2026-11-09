package dev.zernyth.api.world;

/**
 * Native captured state. Explicitly close when no longer needed; disable also frees it.
 */
public interface BlockState extends AutoCloseable {

    /**
     * Reads the native block data.
     *
     * @return the block data
     */
    BlockData getData();

    /**
     * Replaces represented block data. Captured states are applied only by update.
     *
     * @param data the block data to apply or stage
     */
    void setData(BlockData data);

    /**
     * Applies captured state using native force/physics semantics.
     *
     * @param force whether to request a forced native update
     * @param applyPhysics whether to request native physics processing
     * @return whether the native update succeeded
     */
    boolean update(boolean force, boolean applyPhysics);

    /**
     * Releases the registration/state under its server-thread and owner lifetime contract.
     */
    @Override
    void close();
}
