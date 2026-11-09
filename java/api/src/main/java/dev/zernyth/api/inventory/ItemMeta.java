package dev.zernyth.api.inventory;

import java.util.List;
import java.util.Map;

/**
 * Immutable editable metadata. Unknown native NBT is retained by its ItemStack snapshot.
 *
 * @param displayName the custom display name, or null to remove it
 * @param lore the lore lines, copied
 * @param damage the nonnegative durability damage
 * @param unbreakable the native unbreakable metadata flag
 * @param enchantments namespaced enchantment IDs and positive levels, copied
 */
public record ItemMeta(String displayName, List<String> lore, int damage, boolean unbreakable,
                       Map<String, Integer> enchantments) {

    /**
     * Copies lore/enchantments and rejects negative damage, invalid enchantment IDs or nonpositive levels. A
     * null display name clears the custom name.
     *
     * @param displayName the custom display name, or null to remove it
     * @param lore the lore lines, copied
     * @param damage the nonnegative durability damage
     * @param unbreakable the native unbreakable metadata flag
     * @param enchantments namespaced enchantment IDs and positive levels, copied
     */
    public ItemMeta {
        lore = List.copyOf(lore);
        enchantments = Map.copyOf(enchantments);
        if (damage < 0) {
            throw new IllegalArgumentException("Negative damage");
        }
        enchantments.forEach((id, level) -> {
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || level < 1) {
                throw new IllegalArgumentException("Invalid enchantment");
            }
        });
    }

    /**
     * Copies metadata with a replacement display name; null removes the custom name.
     *
     * @param name the declared name
     * @return a new immutable metadata value
     */
    public ItemMeta withDisplayName(String name) {
        return new ItemMeta(name, lore, damage, unbreakable, enchantments);
    }

    /**
     * Copies metadata with a defensively copied lore list.
     *
     * @param lines the lore lines, copied
     * @return a new immutable metadata value
     */
    public ItemMeta withLore(List<String> lines) {
        return new ItemMeta(displayName, lines, damage, unbreakable, enchantments);
    }

    /**
     * Copies metadata with nonnegative damage. Item-specific limits are checked when used.
     *
     * @param value the replacement value; null is permitted only where the enclosing contract specifies
     * @return a new immutable metadata value
     */
    public ItemMeta withDamage(int value) {
        return new ItemMeta(displayName, lore, value, unbreakable, enchantments);
    }
}
