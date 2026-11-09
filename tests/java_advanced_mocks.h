#pragma once
#include "endstone/block/block_state.h"
#include "endstone/enchantments/enchantment.h"
#include "endstone/inventory/item_stack.h"
#include "endstone/inventory/meta/item_meta.h"
#include "endstone/level/chunk.h"
#include "java_gameplay_mocks.h"
namespace endstone {
class AdvancedMockInventory : public PlayerInventory {
public:
    MOCK_METHOD((int), getSize, (), (const, override));
    MOCK_METHOD((int), getMaxStackSize, (), (const, override));
    MOCK_METHOD((std::optional<ItemStack>), getItem, (int), (const, override));
    MOCK_METHOD((void), setItem, (int, std::optional<ItemStack>), (override));
    MOCK_METHOD((std::unordered_map<int, ItemStack>), addItem, (std::vector<ItemStack>), (override));
    MOCK_METHOD((std::unordered_map<int, ItemStack>), removeItem, (std::vector<ItemStack>), (override));
    MOCK_METHOD((std::vector<std::optional<ItemStack>>), getContents, (), (const, override));
    MOCK_METHOD((void), setContents, (std::vector<std::optional<ItemStack>>), (override));
    MOCK_METHOD((bool), contains, (const std::string &), (const, override));
    MOCK_METHOD((bool), contains, (const ItemStack &), (const, override));
    MOCK_METHOD((bool), contains, (const ItemStack &, int), (const, override));
    MOCK_METHOD((bool), containsAtLeast, (const std::string &, int), (const, override));
    MOCK_METHOD((bool), containsAtLeast, (const ItemStack &, int), (const, override));
    MOCK_METHOD((std::unordered_map<int, ItemStack>), all, (const std::string &), (const, override));
    MOCK_METHOD((std::unordered_map<int, ItemStack>), all, (const ItemStack &), (const, override));
    MOCK_METHOD((int), first, (const std::string &), (const, override));
    MOCK_METHOD((int), first, (const ItemStack &), (const, override));
    MOCK_METHOD((int), firstEmpty, (), (const, override));
    MOCK_METHOD((bool), isEmpty, (), (const, override));
    MOCK_METHOD((void), remove, (const std::string &), (override));
    MOCK_METHOD((void), remove, (const ItemStack &), (override));
    MOCK_METHOD((void), clear, (int), (override));
    MOCK_METHOD((void), clear, (), (override));
    MOCK_METHOD((std::optional<ItemStack>), getHelmet, (), (const, override));
    MOCK_METHOD((std::optional<ItemStack>), getChestplate, (), (const, override));
    MOCK_METHOD((std::optional<ItemStack>), getLeggings, (), (const, override));
    MOCK_METHOD((std::optional<ItemStack>), getBoots, (), (const, override));
    MOCK_METHOD((void), setHelmet, (std::optional<ItemStack>), (override));
    MOCK_METHOD((void), setChestplate, (std::optional<ItemStack>), (override));
    MOCK_METHOD((void), setLeggings, (std::optional<ItemStack>), (override));
    MOCK_METHOD((void), setBoots, (std::optional<ItemStack>), (override));
    MOCK_METHOD((std::optional<ItemStack>), getItemInMainHand, (), (const, override));
    MOCK_METHOD((void), setItemInMainHand, (std::optional<ItemStack>), (override));
    MOCK_METHOD((std::optional<ItemStack>), getItemInOffHand, (), (const, override));
    MOCK_METHOD((void), setItemInOffHand, (std::optional<ItemStack>), (override));
    MOCK_METHOD((int), getHeldItemSlot, (), (const, override));
    MOCK_METHOD((void), setHeldItemSlot, (int), (override));
};
class AdvancedMockChunk : public Chunk {
public:
    MOCK_METHOD((int), getX, (), (const, override));
    MOCK_METHOD((int), getZ, (), (const, override));
    MOCK_METHOD((Level &), getLevel, (), (const, override));
    MOCK_METHOD((Dimension &), getDimension, (), (const, override));
};
class AdvancedMockBlockState : public BlockState {
public:
    MOCK_METHOD((std::unique_ptr<Block>), getBlock, (), (const, override));
    MOCK_METHOD((std::string), getType, (), (const, override));
    MOCK_METHOD((void), setType, (std::string), (override));
    MOCK_METHOD((std::unique_ptr<BlockData>), getData, (), (const, override));
    MOCK_METHOD((void), setData, (const BlockData &), (override));
    MOCK_METHOD((Dimension &), getDimension, (), (const, override));
    MOCK_METHOD((int), getX, (), (const, override));
    MOCK_METHOD((int), getY, (), (const, override));
    MOCK_METHOD((int), getZ, (), (const, override));
    MOCK_METHOD((Location), getLocation, (), (const, override));
    MOCK_METHOD((bool), update, (), (override));
    MOCK_METHOD((bool), update, (bool), (override));
    MOCK_METHOD((bool), update, (bool, bool), (override));
};
class AdvancedMockMeta : public ItemMeta {
public:
    MOCK_METHOD((Type), getType, (), (const, override));
    MOCK_METHOD((bool), hasDisplayName, (), (const, override));
    MOCK_METHOD((std::string), getDisplayName, (), (const, override));
    MOCK_METHOD((void), setDisplayName, (std::optional<std::string>), (override));
    MOCK_METHOD((bool), hasLore, (), (const, override));
    MOCK_METHOD((std::vector<std::string>), getLore, (), (const, override));
    MOCK_METHOD((void), setLore, (std::optional<std::vector<std::string>>), (override));
    MOCK_METHOD((bool), hasEnchants, (), (const, override));
    MOCK_METHOD((bool), hasEnchant, (EnchantmentId), (const, override));
    MOCK_METHOD((int), getEnchantLevel, (EnchantmentId), (const, override));
    MOCK_METHOD((std::unordered_map<const Enchantment *, int>), getEnchants, (), (const, override));
    MOCK_METHOD((bool), addEnchant, (EnchantmentId, int, bool), (override));
    MOCK_METHOD((bool), removeEnchant, (EnchantmentId), (override));
    MOCK_METHOD((void), removeEnchants, (), (override));
    MOCK_METHOD((bool), hasConflictingEnchant, (EnchantmentId), (const, override));
    MOCK_METHOD((bool), isUnbreakable, (), (const, override));
    MOCK_METHOD((void), setUnbreakable, (bool), (override));
    MOCK_METHOD((bool), hasDamage, (), (const, override));
    MOCK_METHOD((int), getDamage, (), (const, override));
    MOCK_METHOD((void), setDamage, (int), (override));
    MOCK_METHOD((bool), hasRepairCost, (), (const, override));
    MOCK_METHOD((int), getRepairCost, (), (const, override));
    MOCK_METHOD((void), setRepairCost, (int), (override));
    MOCK_METHOD((std::unique_ptr<ItemMeta>), clone, (), (const, override));
    MOCK_METHOD((const core::ItemMetaExtras &), getExtras, (), (const, override));
};
class AdvancedMockType : public ItemType {
public:
    MOCK_METHOD((std::string), getTranslationKey, (int), (const, override));
    MOCK_METHOD((int), getMaxStackSize, (), (const, override));
    MOCK_METHOD((int), getMaxDurability, (), (const, override));
    MOCK_METHOD((ItemStack), createItemStack, (), (const, override));
    MOCK_METHOD((ItemStack), createItemStack, (int), (const, override));
    MOCK_METHOD((Identifier<ItemType>), getId, (), (const, override));
    MOCK_METHOD((std::string), getTranslationKey, (), (const, override));
};
class AdvancedMockEnchant : public Enchantment {
public:
    MOCK_METHOD((int), getMaxLevel, (), (const, override));
    MOCK_METHOD((int), getStartLevel, (), (const, override));
    MOCK_METHOD((bool), conflictsWith, (const Enchantment &), (const, override));
    MOCK_METHOD((bool), canEnchantItem, (const ItemStack &), (const, override));
    MOCK_METHOD((Identifier<Enchantment>), getId, (), (const, override));
    MOCK_METHOD((std::string), getTranslationKey, (), (const, override));
};
}  // namespace endstone
