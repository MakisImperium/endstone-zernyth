package dev.zernyth.api.player;

import java.util.Objects;

/**
 * Immutable location snapshot. Rotation uses Endstone degrees, pitch followed by yaw.
 *
 * @param dimension the native dimension identifier
 * @param x the X coordinate
 * @param y the Y coordinate
 * @param z the Z coordinate
 * @param pitch the pitch in degrees
 * @param yaw the yaw in degrees
 */
public record Location(String dimension, double x, double y, double z, float pitch, float yaw) {

    /**
     * Requires a dimension identifier and finite coordinates/rotation; native dimension validity is checked
     * when used.
     *
     * @param dimension the native dimension identifier
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param z the Z coordinate
     * @param pitch the pitch in degrees
     * @param yaw the yaw in degrees
     */
    public Location {
        Objects.requireNonNull(dimension);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x) > Float.MAX_VALUE
                || Math.abs(y) > Float.MAX_VALUE || Math.abs(z) > Float.MAX_VALUE || !Float.isFinite(pitch)
                || !Float.isFinite(yaw)) {
            throw new IllegalArgumentException("Non-finite location");
        }
    }
}
