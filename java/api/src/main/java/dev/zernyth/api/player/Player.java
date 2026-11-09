package dev.zernyth.api.player;

import java.util.UUID;

import dev.zernyth.api.command.CommandSender;
import dev.zernyth.api.entity.Entity;
import dev.zernyth.api.form.Form;
import dev.zernyth.api.form.FormRequest;
import dev.zernyth.api.inventory.Inventory;

/**
 * UUID/runtime-session-backed view; never retains a native player pointer.
 * Offline access fails explicitly. A new session with the same UUID invalidates old views.
 * Native operations require the server thread. Defaults for later API additions retain
 * compatibility with older external implementations; Zernyth overrides each in production.
 */
public interface Player extends CommandSender {

    /**
     * Returns the player-session-checked inventory view.
     *
     * @return the live inventory view
     * @throws UnsupportedOperationException if an older external implementation supplies no inventory view
     * @implSpec Compatibility fallback only. Zernyth's player implementation resolves the native inventory.
     */
    default Inventory getInventory() {
        throw new UnsupportedOperationException("Inventory unavailable on this implementation");
    }

    /**
     * Returns the player-session-checked ender-chest view.
     *
     * @return the live ender-chest view
     * @throws UnsupportedOperationException if an older external implementation supplies no ender-chest view
     * @implSpec Compatibility fallback only. Zernyth resolves the native ender-chest container.
     */
    default Inventory getEnderChest() {
        throw new UnsupportedOperationException("Ender chest unavailable on this implementation");
    }

    /**
     * Returns a native entity view for this player session.
     *
     * @return the entity view
     * @throws UnsupportedOperationException if an older external implementation supplies no entity view
     * @implSpec Compatibility fallback only. Zernyth supplies a session-checked entity view.
     */
    default Entity asEntity() {
        throw new UnsupportedOperationException("Entity view unavailable on this implementation");
    }

    /**
     * Sends a native form and returns immediately. Never block waiting for the response.
     *
     * @param form the immutable form description
     * @return the nonblocking response request
     * @throws UnsupportedOperationException if an older external implementation supplies no forms support
     * @implSpec Compatibility fallback only. Zernyth sends through native Endstone forms.
     */
    default FormRequest showForm(Form form) {
        throw new UnsupportedOperationException("Forms unavailable on this implementation");
    }

    /**
     * Returns the player UUID after session validation.
     *
     * @return the player UUID
     */
    UUID getUniqueId();

    /**
     * Checks whether this original player session remains online.
     *
     * @return whether the original player session remains online
     */
    boolean isOnline();

    /**
     * Disconnects the player through the native disconnect path.
     *
     * @param message the message text or immutable component
     */
    void kick(String message);

    /**
     * Queries a detached position and rotation snapshot.
     *
     * @return the detached location
     */
    Location getLocation();

    /**
     * Attempts native teleportation to a validated destination.
     *
     * @param destination the destination including its dimension identifier
     * @return whether native teleportation succeeded
     */
    boolean teleport(Location destination);

    /**
     * Reads the native player game mode.
     *
     * @return the game mode
     */
    GameMode getGameMode();

    /**
     * Changes the native player game mode.
     *
     * @param mode the native game mode
     */
    void setGameMode(GameMode mode);

    /**
     * Reads native health. Entity returns an empty optional when health is unavailable.
     *
     * @return the native health value; Entity returns empty if unavailable
     */
    int getHealth();
}
