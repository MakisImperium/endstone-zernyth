package dev.zernyth.api.world;

/**
 * Integer coordinates; offset rejects arithmetic overflow.
 *
 * @param x the X coordinate
 * @param y the Y coordinate
 * @param z the Z coordinate
 */
public record BlockPosition(int x, int y, int z) {

    /**
     * Creates an offset position using checked integer arithmetic.
     *
     * @param dx the X offset
     * @param dy the Y offset
     * @param dz the Z offset
     * @return the offset value
     */
    public BlockPosition offset(int dx, int dy, int dz) {
        return new BlockPosition(Math.addExact(x, dx), Math.addExact(y, dy), Math.addExact(z, dz));
    }
}
