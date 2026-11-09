package dev.zernyth.api.inventory;

import java.util.List;
import java.util.Map;

/**
 * Server-thread-only live inventory. Reads return independent value snapshots; null means an empty slot.
 */
public interface Inventory {

    /**
     * Kinds supported by the enclosing API; constants do not imply support outside that API.
     */
    enum Type {
        /**
         * Player inventory container.
         */
        PLAYER,
        /**
         * Player ender-chest container.
         */
        ENDER_CHEST
    }

    /**
     * Returns the represented item, block, entity or container type.
     *
     * @return the represented type
     */
    Type getType();

    /**
     * Returns the native slot count; valid slots are zero through size minus one.
     *
     * @return the native slot count
     */
    int getSize();

    /**
     * Returns the native stack-size limit.
     *
     * @return the native maximum stack amount
     */
    int getMaxStackSize();

    /**
     * Copies an item from a validated zero-based slot; null denotes an empty slot.
     *
     * @param slot the zero-based native slot index
     * @return the detached item snapshot, or null for an empty slot
     */
    ItemStack getItem(int slot);

    /**
     * Replaces a zero-based slot; a null item clears that slot.
     *
     * @param slot the zero-based native slot index
     * @param item the item snapshot; null clears a slot where documented
     */
    void setItem(int slot, ItemStack item);

    /**
     * Copies contents in slot order, retaining null entries for empty slots.
     *
     * @return all slot values in order, including null empty entries
     */
    List<ItemStack> getContents();

    /**
     * Replaces native contents with validated item values.
     *
     * @param items the item values in slot/input order
     */
    void setContents(List<ItemStack> items);

    /**
     * Adds items using native stacking rules and returns leftovers keyed by input index.
     *
     * @param items the item values in slot/input order
     * @return leftovers keyed by input index
     */
    Map<Integer, ItemStack> addItems(List<ItemStack> items);

    /**
     * Removes matching items and returns unfulfilled amounts keyed by input index.
     *
     * @param items the item values in slot/input order
     * @return unfulfilled amounts keyed by input index
     */
    Map<Integer, ItemStack> removeItems(List<ItemStack> items);

    /**
     * Checks whether the inventory contains at least the requested matching amount.
     *
     * @param item the item snapshot; null clears a slot where documented
     * @param amount the requested item amount within the enclosing native constraints
     * @return whether enough matching items are present
     */
    boolean contains(ItemStack item, int amount);

    /**
     * Clears the specified slot, or the entire inventory when no slot is supplied.
     */
    void clear();

    /**
     * Clears the specified slot, or the entire inventory when no slot is supplied.
     *
     * @param slot the zero-based native slot index
     */
    void clear(int slot);
}
