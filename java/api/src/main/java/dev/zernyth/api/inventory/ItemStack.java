package dev.zernyth.api.inventory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable native-validated value snapshot. Copies retain all native NBT without native pointers.
 */
public final class ItemStack {

    private final String type, nativeNbt;
    private final int amount, maxStackSize, maxDurability, data;
    private final ItemMeta meta;

    private ItemStack(String type, int amount, int max, int durability, int data, ItemMeta meta, String nbt) {
        if (!type.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || max < 1 || amount < 1 || amount > max
                || durability < 0 || meta.damage() > durability) {
            throw new IllegalArgumentException("Invalid item stack");
        }
        this.type = type;
        this.amount = amount;
        maxStackSize = max;
        maxDurability = durability;
        this.data = data;
        this.meta = meta;
        nativeNbt = nbt;
    }

    /**
     * Returns the represented item, block, entity or container type.
     *
     * @return the represented type
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the immutable stack amount.
     *
     * @return the stack amount
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Returns the native stack-size limit.
     *
     * @return the native maximum stack amount
     */
    public int getMaxStackSize() {
        return maxStackSize;
    }

    /**
     * Returns the native durability limit captured in this snapshot.
     *
     * @return the maximum durability
     */
    public int getMaxDurability() {
        return maxDurability;
    }

    /**
     * Returns the native auxiliary item data captured in this snapshot.
     *
     * @return the native auxiliary item data
     */
    public int getData() {
        return data;
    }

    /**
     * Returns the immutable editable metadata.
     *
     * @return the immutable metadata
     */
    public ItemMeta getItemMeta() {
        return meta;
    }

    /**
     * Creates a snapshot with a validated amount and unchanged native metadata.
     *
     * @param amount the requested item amount within the enclosing native constraints
     * @return a new immutable item snapshot
     */
    public ItemStack withAmount(int amount) {
        return new ItemStack(type, amount, maxStackSize, maxDurability, data, meta, nativeNbt);
    }

    /**
     * Creates a snapshot with replacement metadata, retaining unknown native NBT.
     *
     * @param meta the replacement item metadata, non-null
     * @return a new immutable item snapshot
     */
    public ItemStack withItemMeta(ItemMeta meta) {
        return new ItemStack(
                type, amount, maxStackSize, maxDurability, data, Objects.requireNonNull(meta), nativeNbt);
    }

    /**
     * Copies this immutable snapshot while retaining native metadata.
     *
     * @return an independent immutable item snapshot
     */
    public ItemStack copy() {
        return withAmount(amount);
    }

    /**
     * Compares type, auxiliary data, metadata and native NBT, ignoring amount; null never matches.
     *
     * @param other the comparison item, which may be null
     * @return true for matching metadata/type/NBT ignoring amount; false for null
     */
    public boolean isSimilar(ItemStack other) {
        return other != null && type.equals(other.type) && data == other.data && meta.equals(other.meta)
                && nativeNbt.equals(other.nativeNbt);
    }

    /**
     * Compares value equality, including amount for an ItemStack.
     *
     * @param o the object to compare, which may be null
     * @return whether the value is equal
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof ItemStack other && amount == other.amount && isSimilar(other);
    }

    /**
     * Computes a hash consistent with value equality.
     *
     * @return the equality-consistent hash
     */
    @Override
    public int hashCode() {
        return Objects.hash(type, amount, data, meta, nativeNbt);
    }

    /**
     * Internal binary transport. No native handles. Format is versioned with the runtime, not a persistence
     * contract.
     *
     * @param out the binary destination
     * @throws java.io.IOException if bounded file/transport reading or writing fails
     */
    public void write(DataOutput out) throws IOException {
        put(out, type);
        out.writeInt(amount);
        out.writeInt(maxStackSize);
        out.writeInt(maxDurability);
        out.writeInt(data);
        out.writeBoolean(meta.displayName() != null);
        if (meta.displayName() != null) {
            put(out, meta.displayName());
        }
        out.writeInt(meta.lore().size());
        for (String s : meta.lore()) {
            put(out, s);
        }
        out.writeInt(meta.damage());
        out.writeBoolean(meta.unbreakable());
        out.writeInt(meta.enchantments().size());
        for (var entry : new TreeMap<>(meta.enchantments()).entrySet()) {
            put(out, entry.getKey());
            out.writeInt(entry.getValue());
        }
        put(out, nativeNbt);
    }

    /**
     * Reads bounded runtime-versioned item transport data; this is not a stable persistence format.
     *
     * @param in the bounded binary source
     * @return the decoded item snapshot
     * @throws java.io.IOException if bounded file/transport reading or writing fails
     */
    public static ItemStack read(DataInput in) throws IOException {
        String type = get(in);
        int amount = in.readInt(), max = in.readInt(), durability = in.readInt(), data = in.readInt();
        String name = in.readBoolean() ? get(in) : null;
        var lore = new ArrayList<String>();
        for (int n = count(in); n > 0; n--) {
            lore.add(get(in));
        }
        int damage = in.readInt();
        boolean unbreakable = in.readBoolean();
        var enchants = new TreeMap<String, Integer>();
        for (int n = count(in); n > 0; n--) {
            enchants.put(get(in), in.readInt());
        }
        return new ItemStack(type, amount, max, durability, data,
                new ItemMeta(name, lore, damage, unbreakable, enchants), get(in));
    }

    private static int count(DataInput in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > 65536) {
            throw new IOException("Invalid count");
        }
        return n;
    }

    private static void put(DataOutput out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        if (b.length > 4194304) {
            throw new IOException("Item metadata too large");
        }
        out.writeInt(b.length);
        out.write(b);
    }

    private static String get(DataInput in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > 4194304) {
            throw new IOException("Invalid text size");
        }
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }
}
