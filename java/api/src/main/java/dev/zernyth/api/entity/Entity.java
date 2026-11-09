package dev.zernyth.api.entity;

import java.util.Optional;
import java.util.OptionalInt;

import dev.zernyth.api.player.Location;
import dev.zernyth.api.player.Player;

/**
 * Generation-checked native entity view. Live access requires the server thread and a valid plugin; removed
 * or recycled actors invalidate old handles.
 */
public interface Entity {

    /**
     * Returns the native actor identifier of this entity view.
     *
     * @return the actor ID
     */
    long getId();

    /**
     * Returns the native runtime identifier, distinct from the actor ID.
     *
     * @return the runtime ID
     */
    long getRuntimeId();

    /**
     * Returns the represented item, block, entity or container type.
     *
     * @return the represented type
     */
    String getType();

    /**
     * Checks whether the handle still refers to the original actor lifetime.
     *
     * @return whether the entity handle still identifies its original actor
     */
    boolean isValid();

    /**
     * Queries the native death state.
     *
     * @return whether the entity is dead
     */
    boolean isDead();

    /**
     * Queries a detached position and rotation snapshot.
     *
     * @return the detached location
     */
    Location getLocation();

    /**
     * Attempts native teleportation to a validated destination.
     *
     * @param location the destination including its dimension identifier
     * @return whether native teleportation succeeded
     */
    boolean teleport(Location location);

    /**
     * Changes native entity yaw and pitch.
     *
     * @param yaw the yaw in degrees
     * @param pitch the pitch in degrees
     */
    void setRotation(float yaw, float pitch);

    /**
     * Reads a detached native velocity value.
     *
     * @return the detached velocity
     */
    Velocity getVelocity();

    /**
     * Returns this object's declared or native name.
     *
     * @return the name
     */
    String getName();

    /**
     * Changes the native entity name.
     *
     * @param name the declared name
     */
    void setName(String name);

    /**
     * Queries the native on-ground state.
     *
     * @return whether the entity is on ground
     */
    boolean isOnGround();

    /**
     * Queries the native water state.
     *
     * @return whether the entity is in water
     */
    boolean isInWater();

    /**
     * Queries the native lava state.
     *
     * @return whether the entity is in lava
     */
    boolean isInLava();

    /**
     * Reads native health. Entity returns an empty optional when health is unavailable.
     *
     * @return the native health value; Entity returns empty if unavailable
     */
    OptionalInt getHealth();

    /**
     * Sets health through the supported native health interface.
     *
     * @param health the native health value
     */
    void setHealth(int health);

    /**
     * Resolves a player view, or empty for a non-player entity.
     *
     * @return a player view, or empty for non-player actors
     */
    Optional<Player> asPlayer();

    /**
     * Rejects player entities; use Player.kick instead.
     */
    void remove();

    /**
     * Detached entity velocity components, safe to read on workers and after entity removal.
     *
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param z the Z coordinate
     */
    record Velocity(double x, double y, double z) {

    }
}
