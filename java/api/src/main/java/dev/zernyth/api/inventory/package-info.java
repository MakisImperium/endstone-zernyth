/**
 * Inventories are live, plugin-owned views and require the server thread. Offline players,
 * disabled plugins and invalid slots fail explicitly. Native failures become
 * IllegalStateException; Java value validation uses IllegalArgumentException or
 * NullPointerException. ItemStack and ItemMeta are immutable snapshots and can be
 * copied, compared and prepared on workers. Applying them requires the server thread
 * and revalidates type, amount, durability and enchantments against native registries.
 * Empty slots are null; zero-amount stacks are not supported. Slot layout is the
 * existing Endstone container layout, with no separate Java armor mapping.
 */
package dev.zernyth.api.inventory;
