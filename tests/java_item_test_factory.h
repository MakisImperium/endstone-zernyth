#pragma once
#include "java_advanced_mocks.h"

namespace endstone::core {
// Narrow friend access constructs a pure public-API ItemStack test implementation. No BDS.
class ItemStackTestFactory {
    struct Meta {
        std::optional<std::string> name;
        std::vector<std::string> lore;
        int damage = 0;
        bool unbreakable = false;
        std::unordered_map<const Enchantment *, int> enchants;
    };
    class Impl final : public ItemStack::Impl {
    public:
        const ItemType *type;
        const Enchantment *enchantment;
        int amount, data = 0;
        Meta meta;
        CompoundTag nbt;
        Impl(const ItemType &t, int a, const Enchantment *e) : type(&t), enchantment(e), amount(a)
        {
            nbt["unknown_plugin_data"] = StringTag("preserve-me");
            ListTag list;
            list.emplace_back(StringTag("first"));
            list.emplace_back(StringTag("second"));
            nbt["unknown_list"] = std::move(list);
            nbt["exact_long"] = LongTag(INT64_C(9223372036854775806));
            nbt["bytes"] = ByteArrayTag({0, 127, 255});
            nbt["ints"] = IntArrayTag({-1, 0, 2147483647});
            nbt["float"] = FloatTag(1.25f);
            nbt["double"] = DoubleTag(2.5);
            CompoundTag nested;
            nested["short"] = ShortTag(-123);
            nested["byte"] = ByteTag(255);
            nbt["nested"] = std::move(nested);
        }
        std::unique_ptr<ItemStack::Impl> clone() const override { return std::make_unique<Impl>(*this); }
        const ItemType &getType() const override { return *type; }
        void setType(ItemTypeId) override { throw std::logic_error("Not needed by value bridge tests"); }
        int getAmount() const override { return amount; }
        void setAmount(int value) override { amount = value; }
        int getData() const override { return data; }
        void setData(int value) override { data = value; }
        std::string getTranslationKey() const override { return "item.test"; }
        int getMaxStackSize() const override { return type->getMaxStackSize(); }
        bool isSimilar(const ItemStack::Impl &other) const override
        {
            auto *v = dynamic_cast<const Impl *>(&other);
            return v && type == v->type && data == v->data && meta.name == v->meta.name && meta.lore == v->meta.lore &&
                   meta.damage == v->meta.damage && meta.enchants == v->meta.enchants && nbt == v->nbt;
        }
        std::unique_ptr<ItemMeta> getItemMeta() const override
        {
            using testing::_;
            using testing::Return;
            auto result = std::make_unique<testing::NiceMock<AdvancedMockMeta>>();
            auto value = std::make_shared<Meta>(meta);
            ON_CALL(*result, hasDisplayName()).WillByDefault([value] { return value->name.has_value(); });
            ON_CALL(*result, getDisplayName()).WillByDefault([value] { return value->name.value_or(""); });
            ON_CALL(*result, setDisplayName(_)).WillByDefault([value](std::optional<std::string> s) {
                value->name = std::move(s);
            });
            ON_CALL(*result, getLore()).WillByDefault([value] { return value->lore; });
            ON_CALL(*result, setLore(_)).WillByDefault([value](std::optional<std::vector<std::string>> s) {
                value->lore = s.value_or(std::vector<std::string>{});
            });
            ON_CALL(*result, getDamage()).WillByDefault([value] { return value->damage; });
            ON_CALL(*result, setDamage(_)).WillByDefault([value](int d) { value->damage = d; });
            ON_CALL(*result, isUnbreakable()).WillByDefault([value] { return value->unbreakable; });
            ON_CALL(*result, setUnbreakable(_)).WillByDefault([value](bool b) { value->unbreakable = b; });
            ON_CALL(*result, getEnchants()).WillByDefault([value] { return value->enchants; });
            ON_CALL(*result, removeEnchants()).WillByDefault([value] { value->enchants.clear(); });
            ON_CALL(*result, addEnchant(_, _, _))
                .WillByDefault([value, e = enchantment](EnchantmentId id, int level, bool) {
                    if (!e || id != e->getId() || level < 1 || level > e->getMaxLevel()) {
                        return false;
                    }
                    value->enchants[e] = level;
                    return true;
                });
            return result;
        }
        bool hasItemMeta() const override { return true; }
        bool setItemMeta(const ItemMeta *m) override
        {
            if (!m) {
                meta = {};
                return true;
            }
            meta.name = m->hasDisplayName() ? std::optional<std::string>(m->getDisplayName()) : std::nullopt;
            meta.lore = m->getLore();
            meta.damage = m->getDamage();
            meta.unbreakable = m->isUnbreakable();
            meta.enchants = m->getEnchants();
            return true;
        }
        CompoundTag getNbt() const override { return nbt; }
        void setNbt(const CompoundTag &tag) override { nbt = tag; }
    };

public:
    static ItemStack create(const ItemType &type, int amount, const Enchantment *enchantment = nullptr)
    {
        return ItemStack(std::make_unique<Impl>(type, amount, enchantment));
    }
};
}  // namespace endstone::core
