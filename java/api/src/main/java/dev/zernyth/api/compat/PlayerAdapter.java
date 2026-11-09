package dev.zernyth.api.compat;

import java.util.Objects;
import java.util.UUID;

import dev.zernyth.api.player.Location;
import dev.zernyth.api.player.Player;
import dev.zernyth.api.text.Component;

/**
 * Source-porting facade for selected familiar player operations.
 * Not org.bukkit.Player, not a loader for unchanged Bukkit/Paper JARs.
 * All methods retain the underlying player's server-thread and lifetime checks.
 */
public final class PlayerAdapter {

    private final Player player;

    private PlayerAdapter(Player player) {
        this.player = Objects.requireNonNull(player);
    }

    /**
     * Creates a limited player porting view without changing native lifetime or threading restrictions.
     *
     * @param player the associated session-checked player view
     * @return the limited player adapter
     */
    public static PlayerAdapter adapt(Player player) {
        return new PlayerAdapter(player);
    }

    /**
     * Returns this object's declared or native name.
     *
     * @return the name
     */
    public String getName() {
        return player.getName();
    }

    /**
     * Returns the player UUID after session validation.
     *
     * @return the player UUID
     */
    public UUID getUniqueId() {
        return player.getUniqueId();
    }

    /**
     * Checks whether this original player session remains online.
     *
     * @return whether the original player session remains online
     */
    public boolean isOnline() {
        return player.isOnline();
    }

    /**
     * Delivers text through native messaging. Translation components require implementation support.
     *
     * @param message the message text or immutable component
     */
    public void sendMessage(Component message) {
        player.sendMessage(message);
    }

    /**
     * Checks the native permission system.
     *
     * @param permission the permission node; an empty command permission adds no extra node
     * @return whether the permission is granted
     */
    public boolean hasPermission(String permission) {
        return player.hasPermission(Objects.requireNonNull(permission));
    }

    /**
     * Queries a detached position and rotation snapshot.
     *
     * @return the detached location
     */
    public Location getLocation() {
        return player.getLocation();
    }

    /**
     * Attempts native teleportation to a validated destination.
     *
     * @param location the destination including its dimension identifier
     * @return whether native teleportation succeeded
     */
    public boolean teleport(Location location) {
        return player.teleport(Objects.requireNonNull(location));
    }
}
