#include <bit>
#include <set>

#include <nlohmann/json.hpp>

#include "endstone/block/block_data.h"
#include "endstone/block/block_state.h"
#include "endstone/core/plugin/java_gameplay_internal.h"
#include "endstone/enchantments/enchantment.h"
#include "endstone/event/actor/actor_remove_event.h"
#include "endstone/inventory/item_type.h"
#include "endstone/inventory/meta/item_meta.h"
#include "endstone/level/chunk.h"
#include "endstone/nbt/tag.h"

namespace endstone::core::java::details {
using Json = nlohmann::json;
namespace {
constexpr std::size_t MaxPayload = 8 * 1024 * 1024;
struct Writer {
    std::vector<jbyte> data;
    void byte(unsigned n)
    {
        if (data.size() >= MaxPayload) {
            throw std::runtime_error("Advanced response too large");
        }
        data.push_back(static_cast<jbyte>(n));
    }
    void integer(std::int32_t n)
    {
        auto value = static_cast<std::uint32_t>(n);
        for (int i = 3; i >= 0; --i) {
            byte(value >> (i * 8));
        }
    }
    void wide(std::int64_t n)
    {
        auto value = static_cast<std::uint64_t>(n);
        for (int i = 7; i >= 0; --i) {
            byte(static_cast<unsigned>(value >> (i * 8)));
        }
    }
    void number(double n) { wide(std::bit_cast<std::int64_t>(n)); }
    void text(const std::string &s)
    {
        if (s.size() > 4 * 1024 * 1024) {
            throw std::runtime_error("Text too large");
        }
        integer(static_cast<int>(s.size()));
        for (auto c : s) {
            byte(static_cast<unsigned char>(c));
        }
    }
};
struct Reader {
    std::vector<jbyte> data;
    std::size_t at = 0;
    Reader(JNIEnv *e, jbyteArray input)
    {
        if (!input) {
            throw std::invalid_argument("Null advanced request");
        }
        auto n = e->GetArrayLength(input);
        checked(e);
        if (n < 0 || static_cast<std::size_t>(n) > MaxPayload) {
            throw std::runtime_error("Request too large");
        }
        data.resize(n);
        e->GetByteArrayRegion(input, 0, n, data.data());
        checked(e);
    }
    unsigned byte()
    {
        if (at == data.size()) {
            throw std::runtime_error("Truncated advanced request");
        }
        return static_cast<unsigned char>(data[at++]);
    }
    std::int32_t integer()
    {
        std::uint32_t n = 0;
        for (int i = 0; i < 4; ++i) {
            n = (n << 8) | byte();
        }
        return std::bit_cast<std::int32_t>(n);
    }
    std::int64_t wide()
    {
        std::uint64_t n = 0;
        for (int i = 0; i < 8; ++i) {
            n = (n << 8) | byte();
        }
        return std::bit_cast<std::int64_t>(n);
    }
    double number()
    {
        auto n = std::bit_cast<double>(wide());
        if (!std::isfinite(n)) {
            throw std::runtime_error("Non-finite number");
        }
        return n;
    }
    bool boolean()
    {
        auto n = byte();
        if (n > 1) {
            throw std::runtime_error("Invalid boolean");
        }
        return n != 0;
    }
    int count(int limit = 65536)
    {
        int n = integer();
        if (n < 0 || n > limit) {
            throw std::runtime_error("Invalid collection length");
        }
        return n;
    }
    std::string text()
    {
        auto n = count(4 * 1024 * 1024);
        if (static_cast<std::size_t>(n) > data.size() - at) {
            throw std::runtime_error("Truncated text");
        }
        std::string s(reinterpret_cast<const char *>(data.data() + at), n);
        at += n;
        return s;
    }
    void done()
    {
        if (at != data.size()) {
            throw std::runtime_error("Trailing request data");
        }
    }
};
jbyteArray bytes(JNIEnv *e, const Writer &w)
{
    auto array = e->NewByteArray(static_cast<jsize>(w.data.size()));
    checked(e);
    e->SetByteArrayRegion(array, 0, static_cast<jsize>(w.data.size()), w.data.data());
    checked(e);
    return array;
}
bool identifier(const std::string &s)
{
    auto colon = s.find(':');
    return colon != std::string::npos && colon > 0 && colon + 1 < s.size() &&
           s.find(':', colon + 1) == std::string::npos &&
           s.find_first_not_of("abcdefghijklmnopqrstuvwxyz0123456789_.-:/") == std::string::npos;
}
Json tagJson(const endstone::nbt::Tag &tag, int depth = 0)
{
    if (depth > 64) {
        throw std::runtime_error("NBT nesting limit");
    }
    Json value;
    tag.visit([&](const auto &v) {
        using T = std::decay_t<decltype(v)>;
        if constexpr (std::is_same_v<T, std::monostate>) {
            value = nullptr;
        }
        else if constexpr (std::is_same_v<T, CompoundTag>) {
            value = Json::object();
            for (const auto &[key, child] : v) {
                value[key] = tagJson(child, depth + 1);
            }
        }
        else if constexpr (std::is_same_v<T, ListTag>) {
            value = Json::array();
            for (const auto &child : v) {
                value.push_back(tagJson(child, depth + 1));
            }
        }
        else if constexpr (std::is_same_v<T, ByteArrayTag> || std::is_same_v<T, IntArrayTag>) {
            value = Json::array();
            for (auto child : v) {
                value.push_back(child);
            }
        }
        else if constexpr (std::is_same_v<T, FloatTag>) {
            value = std::bit_cast<std::uint32_t>(v.value());
        }
        else if constexpr (std::is_same_v<T, DoubleTag>) {
            value = std::bit_cast<std::uint64_t>(v.value());
        }
        else {
            value = v.value();
        }
    });
    return Json{{"t", static_cast<int>(tag.type())}, {"v", value}};
}
endstone::nbt::Tag jsonTag(const Json &j, int depth = 0)
{
    if (depth > 64) {
        throw std::runtime_error("NBT nesting limit");
    }
    auto &v = j.at("v");
    switch (j.at("t").get<int>()) {
    case 0:
        return {};
    case 1:
        return ByteTag(v.get<std::uint8_t>());
    case 2:
        return ShortTag(v.get<std::int16_t>());
    case 3:
        return IntTag(v.get<std::int32_t>());
    case 4:
        return LongTag(v.get<std::int64_t>());
    case 5:
        return FloatTag(std::bit_cast<float>(v.get<std::uint32_t>()));
    case 6:
        return DoubleTag(std::bit_cast<double>(v.get<std::uint64_t>()));
    case 7:
        return ByteArrayTag(v.get<std::vector<std::uint8_t>>());
    case 8:
        return StringTag(v.get<std::string>());
    case 9: {
        ListTag out;
        if (v.size() > 65536) {
            throw std::runtime_error("NBT list too large");
        }
        for (const auto &child : v) {
            out.emplace_back(jsonTag(child, depth + 1));
        }
        return out;
    }
    case 10: {
        CompoundTag out;
        if (!v.is_object() || v.size() > 65536) {
            throw std::runtime_error("Invalid NBT compound");
        }
        for (auto it = v.begin(); it != v.end(); ++it) {
            auto child = jsonTag(it.value(), depth + 1);
            if (child.type() == endstone::nbt::Type::End) {
                throw std::runtime_error("End tag inside NBT compound");
            }
            out[it.key()] = std::move(child);
        }
        return out;
    }
    case 11:
        return IntArrayTag(v.get<std::vector<std::int32_t>>());
    default:
        throw std::runtime_error("Unknown NBT type");
    }
}
const ItemType &itemType(Context &c, const std::string &type)
{
    if (type == "minecraft:air") {
        throw std::invalid_argument("Use an empty inventory slot, not an air ItemStack");
    }
    if (!identifier(type)) {
        throw std::invalid_argument("Invalid item identifier");
    }
    auto *registry = dynamic_cast<const Registry<ItemType> *>(c.server->_getRegistry(ItemType::RegistryType));
    if (!registry) {
        throw std::runtime_error("Item registry unavailable");
    }
    auto *entry = registry->get(ItemTypeId(type));
    if (!entry) {
        throw std::runtime_error("Unknown item type");
    }
    return *entry;
}
void writeItem(Writer &w, const ItemStack &item)
{
    w.text(static_cast<std::string>(item.getType().getId()));
    w.integer(item.getAmount());
    w.integer(item.getMaxStackSize());
    w.integer(item.getType().getMaxDurability());
    w.integer(item.getData());
    auto meta = item.getItemMeta();
    w.byte(meta && meta->hasDisplayName());
    if (meta && meta->hasDisplayName()) {
        w.text(meta->getDisplayName());
    }
    auto lore = meta ? meta->getLore() : std::vector<std::string>{};
    w.integer(static_cast<int>(lore.size()));
    for (const auto &s : lore) {
        w.text(s);
    }
    w.integer(meta ? meta->getDamage() : 0);
    w.byte(meta && meta->isUnbreakable());
    std::map<std::string, int> enchants;
    if (meta) {
        for (const auto &[enchantment, level] : meta->getEnchants()) {
            enchants.emplace(static_cast<std::string>(enchantment->getId()), level);
        }
    }
    w.integer(static_cast<int>(enchants.size()));
    for (const auto &[key, level] : enchants) {
        w.text(key);
        w.integer(level);
    }
    // Keep unknown/specialized NBT, but normalize editable common metadata out of it.
    // Otherwise withItemMeta(...).equals(inventory.getItem(...)) would compare stale old names.
    auto copy = item;
    auto common = copy.getItemMeta();
    if (common) {
        common->setDisplayName(std::nullopt);
        common->setLore(std::nullopt);
        common->setDamage(0);
        common->setUnbreakable(false);
        common->removeEnchants();
        if (!copy.setItemMeta(common.get())) {
            throw std::runtime_error("Cannot snapshot item metadata");
        }
    }
    auto opaque = copy.getNbt();
    if (opaque.contains("display") && opaque.at("display").type() == endstone::nbt::Type::Compound &&
        opaque.at("display").get<CompoundTag>().empty()) {
        opaque.erase("display");
    }
    w.text(tagJson(endstone::nbt::Tag(opaque)).dump());
}
ItemStack readItem(Context &c, Reader &r)
{
    auto type = r.text();
    int amount = r.integer();
    (void)r.integer();
    (void)r.integer();
    int data = r.integer();
    const auto &nativeType = itemType(c, type);
    if (amount < 1 || amount > nativeType.getMaxStackSize() || data < 0 || data > 65535) {
        throw std::invalid_argument("Invalid item amount/data");
    }
    std::optional<std::string> name;
    if (r.boolean()) {
        name = r.text();
    }
    std::vector<std::string> lore;
    for (int n = r.count(); n > 0; --n) {
        lore.push_back(r.text());
    }
    int damage = r.integer();
    bool unbreakable = r.boolean();
    std::map<std::string, int> enchants;
    for (int n = r.count(); n > 0; --n) {
        auto name = r.text();
        int level = r.integer();
        if (level < 1 || !identifier(name)) {
            throw std::invalid_argument("Invalid enchantment");
        }
        enchants.emplace(name, level);
    }
    auto opaque = r.text();
    auto tag = jsonTag(Json::parse(opaque));
    auto item = nativeType.createItemStack(amount);
    item.setData(data);
    item.setNbt(tag.get<CompoundTag>());
    if (damage < 0 || damage > nativeType.getMaxDurability()) {
        throw std::invalid_argument("Invalid item damage");
    }
    auto meta = item.getItemMeta();
    if (!meta) {
        throw std::runtime_error("Native item metadata unavailable");
    }
    meta->setDisplayName(name);
    meta->setLore(lore);
    meta->setDamage(damage);
    meta->setUnbreakable(unbreakable);
    meta->removeEnchants();
    for (const auto &[key, level] : enchants) {
        if (!meta->addEnchant(EnchantmentId(key), level, false)) {
            throw std::runtime_error("Native enchantment validation failed");
        }
    }
    if (!item.setItemMeta(meta.get())) {
        throw std::runtime_error("Native metadata rejected");
    }
    return item;
}
void optionalItem(Writer &w, const std::optional<ItemStack> &item)
{
    w.byte(item.has_value());
    if (item) {
        writeItem(w, *item);
    }
}
std::optional<ItemStack> optionalItem(Context &c, Reader &r)
{
    if (!r.boolean()) {
        return {};
    }
    return readItem(c, r);
}
Inventory &inventory(Context &c, jlong p, bool ender)
{
    auto &owner = player(c, p);
    return ender ? owner.getEnderChest() : owner.getInventory();
}
void slot(const Inventory &inv, int index)
{
    if (index < 0 || index >= inv.getSize()) {
        throw std::out_of_range("Inventory slot outside native bounds");
    }
}
struct WorldRef {
    Level *identity;
    std::string name;
    bool valid = true;
};
struct EntityRef {
    jlong world;
    std::int64_t id;
    std::uint64_t runtime;
    bool valid = true;
};
struct CapturedBlock {
    jlong world;
    std::string dimension;
    int x, y, z;
    std::unique_ptr<BlockState> state;
};
struct FormState {
    UUID player;
    Global callback;
    jmethodID method;
    Json schema;
};
}  // namespace
struct AdvancedState {
    std::unordered_map<jlong, WorldRef> worlds;
    std::unordered_map<jlong, EntityRef> entities;
    std::unordered_map<jlong, CapturedBlock> blocks;
    std::unordered_map<jlong, FormState> forms;
    std::set<UUID> disconnected;
    EventHandler *quit = nullptr, *remove = nullptr, *join = nullptr;
};
namespace {
void finish(const std::shared_ptr<Context> &c, jlong token, int status, const Json &values = Json::array());
AdvancedState &state(const std::shared_ptr<Context> &c)
{
    if (!c->advanced) {
        c->advanced = std::make_shared<AdvancedState>();
    }
    auto &s = *c->advanced;
    if (c->plugin->isEnabled() && !s.quit) {
        std::weak_ptr<Context> weak = c;
        s.join = manager(*c).registerManagedEvent(
            PlayerJoinEvent::NAME,
            [weak](Event &event) {
                auto c = weak.lock();
                if (!c || !c->active || !c->advanced) {
                    return;
                }
                c->advanced->disconnected.erase(static_cast<PlayerJoinEvent &>(event).getPlayer().getUniqueId());
            },
            EventPriority::Lowest, *c->plugin, false);
        s.quit = manager(*c).registerManagedEvent(
            PlayerQuitEvent::NAME,
            [weak](Event &event) {
                auto c = weak.lock();
                if (!c || !c->active || !c->advanced) {
                    return;
                }
                auto &p = static_cast<PlayerQuitEvent &>(event).getPlayer();
                auto uuid = p.getUniqueId();
                auto actorId = p.getId();
                auto runtime = p.getRuntimeId();
                auto pinned = c->advanced;
                pinned->disconnected.insert(uuid);
                std::vector<jlong> pending;
                for (const auto &[id, form] : pinned->forms) {
                    if (form.player == uuid) {
                        pending.push_back(id);
                    }
                }
                for (auto &[id, entity] : pinned->entities) {
                    if (entity.id == actorId && entity.runtime == runtime) {
                        entity.valid = false;
                    }
                }
                for (auto id : pending) {
                    finish(c, id, 2);
                }
            },
            EventPriority::Lowest, *c->plugin, false);
        s.remove = manager(*c).registerManagedEvent(
            ActorRemoveEvent::NAME,
            [weak](Event &event) {
                auto c = weak.lock();
                if (!c || !c->active || !c->advanced) {
                    return;
                }
                auto &actor = static_cast<ActorRemoveEvent &>(event).getActor();
                for (auto &[id, entity] : c->advanced->entities) {
                    if (entity.id == actor.getId() && entity.runtime == actor.getRuntimeId()) {
                        entity.valid = false;
                    }
                }
            },
            EventPriority::Lowest, *c->plugin, false);
    }
    return s;
}
jlong worldId(AdvancedState &s, Level &world)
{
    for (const auto &[id, entry] : s.worlds) {
        if (entry.valid && entry.identity == &world && entry.name == world.getName()) {
            return id;
        }
    }
    auto token = id();
    s.worlds.emplace(token, WorldRef{&world, world.getName()});
    return token;
}
Level &world(Context &c, AdvancedState &s, jlong token)
{
    auto it = s.worlds.find(token);
    if (it == s.worlds.end() || !it->second.valid) {
        throw std::runtime_error("Invalid world handle");
    }
    auto *current = c.server->getLevel();
    if (current != it->second.identity || !current || current->getName() != it->second.name) {
        it->second.valid = false;
        throw std::runtime_error("World unloaded or replaced");
    }
    return *current;
}
Dimension &dimension(Context &c, AdvancedState &s, jlong token, const std::string &name)
{
    auto *d = world(c, s, token).getDimension(name);
    if (!d) {
        throw std::runtime_error("Invalid dimension");
    }
    return *d;
}
int chunkCoordinate(int block)
{
    int q = block / 16;
    return block < 0 && block % 16 ? q - 1 : q;
}
void loaded(Dimension &d, int x, int z)
{
    int cx = chunkCoordinate(x), cz = chunkCoordinate(z);
    for (const auto &chunk : d.getLoadedChunks()) {
        if (chunk && chunk->getX() == cx && chunk->getZ() == cz) {
            return;
        }
    }
    throw std::runtime_error("Chunk is not loaded; implicit chunk loading is forbidden");
}
std::unique_ptr<Block> block(Context &c, AdvancedState &s, jlong token, const std::string &name, int x, int y, int z)
{
    auto &d = dimension(c, s, token, name);
    loaded(d, x, z);
    auto b = d.getBlockAt(x, y, z);
    if (!b) {
        throw std::runtime_error("Native block unavailable");
    }
    return b;
}
void location(Writer &w, const Location &l)
{
    w.text(l.getDimension().getName());
    w.number(l.getX());
    w.number(l.getY());
    w.number(l.getZ());
    w.number(l.getPitch());
    w.number(l.getYaw());
}
Location location(Context &c, AdvancedState &s, jlong token, Reader &r, bool checkChunk = true)
{
    auto name = r.text();
    double x = r.number(), y = r.number(), z = r.number(), pitch = r.number(), yaw = r.number();
    if (x < std::numeric_limits<int>::min() || x > std::numeric_limits<int>::max() ||
        z < std::numeric_limits<int>::min() || z > std::numeric_limits<int>::max() ||
        std::abs(y) > std::numeric_limits<float>::max() || std::abs(pitch) > std::numeric_limits<float>::max() ||
        std::abs(yaw) > std::numeric_limits<float>::max()) {
        throw std::runtime_error("Invalid location");
    }
    auto &d = dimension(c, s, token, name);
    if (checkChunk) {
        loaded(d, static_cast<int>(std::floor(x)), static_cast<int>(std::floor(z)));
    }
    return Location(d, x, y, z, static_cast<float>(pitch), static_cast<float>(yaw));
}
void blockData(Writer &w, const BlockData &data)
{
    w.text(data.getType());
    std::map<std::string, BlockStates::mapped_type> states;
    for (const auto &[key, value] : data.getBlockStates()) {
        states.emplace(key, value);
    }
    w.integer(static_cast<int>(states.size()));
    for (const auto &[key, value] : states) {
        w.text(key);
        std::visit(
            [&](auto &&v) {
                using T = std::decay_t<decltype(v)>;
                if constexpr (std::is_same_v<T, std::string>) {
                    w.byte(0);
                    w.text(v);
                }
                else if constexpr (std::is_same_v<T, int>) {
                    w.byte(1);
                    w.integer(v);
                }
                else {
                    w.byte(2);
                    w.byte(v);
                }
            },
            value);
    }
}
std::unique_ptr<BlockData> blockData(Context &c, Reader &r)
{
    auto type = r.text();
    if (!identifier(type)) {
        throw std::runtime_error("Invalid block type");
    }
    BlockStates properties;
    for (int n = r.count(); n > 0; --n) {
        auto key = r.text();
        auto kind = r.byte();
        if (kind == 0) {
            properties.emplace(key, r.text());
        }
        else if (kind == 1) {
            properties.emplace(key, r.integer());
        }
        else if (kind == 2) {
            properties.emplace(key, r.boolean());
        }
        else {
            throw std::runtime_error("Invalid block property");
        }
    }
    auto result = c.server->createBlockData(type, properties);
    if (!result) {
        throw std::runtime_error("Block data unavailable");
    }
    return result;
}
jlong entityId(AdvancedState &s, jlong worldToken, Actor &actor)
{
    // Tokens are never reused. Retire bookkeeping as well as references; old Java
    // wrappers still resolve as invalid without retaining every despawn forever.
    std::erase_if(s.entities, [](const auto &entry) { return !entry.second.valid; });
    for (const auto &[id, entry] : s.entities) {
        if (entry.valid && entry.world == worldToken && entry.id == actor.getId() &&
            entry.runtime == actor.getRuntimeId()) {
            return id;
        }
    }
    auto token = id();
    s.entities.emplace(token, EntityRef{worldToken, actor.getId(), actor.getRuntimeId()});
    return token;
}
Actor *resolveEntity(Context &c, AdvancedState &s, jlong token)
{
    auto it = s.entities.find(token);
    if (it == s.entities.end()) {
        return nullptr;
    }
    if (!it->second.valid) {
        return nullptr;
    }
    Level *level = nullptr;
    try {
        level = &world(c, s, it->second.world);
    }
    catch (const std::runtime_error &) {
        it->second.valid = false;
        return nullptr;
    }
    for (auto *actor : level->getActors()) {
        if (actor && actor->getId() == it->second.id && actor->getRuntimeId() == it->second.runtime &&
            actor->isValid()) {
            return actor;
        }
    }
    it->second.valid = false;
    return nullptr;
}
Actor &entity(Context &c, AdvancedState &s, jlong token)
{
    auto *a = resolveEntity(c, s, token);
    if (!a) {
        throw std::runtime_error("Entity removed or stale");
    }
    return *a;
}
void advancedOperation(const std::shared_ptr<Context> &c, int op, jlong token, Reader &r, Writer &w)
{
    auto &s = state(c);
    auto pinned = c->advanced;  // Native mutations can reenter plugin disable via other native listeners.
    if (op == 1) {
        auto type = r.text();
        int amount = r.integer();
        r.done();
        auto &t = itemType(*c, type);
        if (amount < 1 || amount > t.getMaxStackSize()) {
            throw std::runtime_error("Invalid item quantity");
        }
        writeItem(w, t.createItemStack(amount));
        return;
    }
    if (op >= 10 && op <= 17) {
        auto &inv = inventory(*c, token, r.boolean());
        switch (op) {
        case 10:
            r.done();
            w.integer(inv.getSize());
            w.integer(inv.getMaxStackSize());
            break;
        case 11: {
            int index = r.integer();
            r.done();
            slot(inv, index);
            optionalItem(w, inv.getItem(index));
            break;
        }
        case 12: {
            int index = r.integer();
            slot(inv, index);
            auto item = optionalItem(*c, r);
            r.done();
            if (item && item->getAmount() > inv.getMaxStackSize()) {
                throw std::runtime_error("Inventory stack limit");
            }
            inv.setItem(index, std::move(item));
            break;
        }
        case 13: {
            r.done();
            auto content = inv.getContents();
            w.integer(static_cast<int>(content.size()));
            for (const auto &item : content) {
                optionalItem(w, item);
            }
            break;
        }
        case 14: {
            int count = r.count();
            if (count > inv.getSize()) {
                throw std::runtime_error("Inventory contents exceed capacity");
            }
            std::vector<std::optional<ItemStack>> content;
            for (int i = 0; i < count; ++i) {
                auto item = optionalItem(*c, r);
                if (item && item->getAmount() > inv.getMaxStackSize()) {
                    throw std::runtime_error("Inventory stack limit");
                }
                content.push_back(std::move(item));
            }
            r.done();
            inv.setContents(std::move(content));
            break;
        }
        case 15: {
            bool remove = r.boolean();
            std::vector<ItemStack> items;
            for (int n = r.count(); n > 0; --n) {
                items.push_back(readItem(*c, r));
            }
            r.done();
            auto remaining = remove ? inv.removeItem(std::move(items)) : inv.addItem(std::move(items));
            w.integer(static_cast<int>(remaining.size()));
            for (const auto &[index, item] : remaining) {
                w.integer(index);
                writeItem(w, item);
            }
            break;
        }
        case 16: {
            auto item = readItem(*c, r);
            int count = r.integer();
            r.done();
            if (count < 0) {
                throw std::runtime_error("Negative amount");
            }
            w.byte(inv.contains(item, count));
            break;
        }
        case 17: {
            int index = r.integer();
            r.done();
            if (index == -1) {
                inv.clear();
            }
            else {
                slot(inv, index);
                inv.clear(index);
            }
            break;
        }
        }
        return;
    }
    switch (op) {
    case 20: {
        r.done();
        auto *level = c->server->getLevel();
        w.integer(level ? 1 : 0);
        if (level) {
            w.wide(worldId(s, *level));
            w.text(level->getName());
        }
        break;
    }
    case 21: {
        r.done();
        auto &level = world(*c, s, token);
        auto dims = level.getDimensions();
        w.integer(static_cast<int>(dims.size() + 1));
        w.text(level.getName());
        for (auto *d : dims) {
            if (!d) {
                throw std::runtime_error("Null dimension");
            }
            w.text(d->getName());
        }
        break;
    }
    case 22: {
        auto name = r.text();
        r.done();
        auto chunks = dimension(*c, s, token, name).getLoadedChunks();
        w.integer(static_cast<int>(chunks.size()));
        for (const auto &chunk : chunks) {
            if (!chunk) {
                throw std::runtime_error("Null chunk");
            }
            w.integer(chunk->getX());
            w.integer(chunk->getZ());
        }
        break;
    }
    case 23:
    case 24:
    case 25: {
        auto name = r.text();
        int x = r.integer(), y = r.integer(), z = r.integer();
        auto b = block(*c, s, token, name, x, y, z);
        if (op == 23) {
            r.done();
            auto data = b->getData();
            if (!data) {
                throw std::runtime_error("No block data");
            }
            blockData(w, *data);
        }
        if (op == 24) {
            auto data = blockData(*c, r);
            bool physics = r.boolean();
            r.done();
            b->setData(*data, physics);
        }
        if (op == 25) {
            r.done();
            auto captured = b->captureState();
            if (!captured) {
                throw std::runtime_error("No block state");
            }
            auto idValue = id();
            s.blocks.emplace(idValue, CapturedBlock{token, name, x, y, z, std::move(captured)});
            w.wide(idValue);
        }
        break;
    }
    case 26:
    case 27:
    case 28:
    case 29: {
        auto it = s.blocks.find(token);
        if (op == 29) {
            r.done();
            if (it != s.blocks.end()) {
                s.blocks.erase(it);
            }
            break;
        }
        if (it == s.blocks.end()) {
            throw std::runtime_error("Expired block snapshot");
        }
        auto &entry = it->second;
        (void)block(*c, s, entry.world, entry.dimension, entry.x, entry.y, entry.z);
        if (op == 26) {
            r.done();
            auto data = entry.state->getData();
            if (!data) {
                throw std::runtime_error("No block state data");
            }
            blockData(w, *data);
        }
        if (op == 27) {
            auto data = blockData(*c, r);
            r.done();
            entry.state->setData(*data);
        }
        if (op == 28) {
            bool force = r.boolean(), physics = r.boolean();
            r.done();
            w.byte(entry.state->update(force, physics));
        }
        break;
    }
    case 30: {
        r.done();
        auto actors = world(*c, s, token).getActors();
        std::vector<jlong> result;
        for (auto *actor : actors) {
            if (actor && actor->isValid()) {
                result.push_back(entityId(s, token, *actor));
            }
        }
        w.integer(static_cast<int>(result.size()));
        for (auto idValue : result) {
            w.wide(idValue);
        }
        break;
    }
    case 31: {
        r.done();
        auto &a = entity(*c, s, token);
        w.wide(a.getId());
        w.wide(static_cast<std::int64_t>(a.getRuntimeId()));
        w.text(a.getType());
        w.byte(a.isValid());
        w.byte(a.isDead());
        location(w, a.getLocation());
        auto v = a.getVelocity();
        w.number(v.getX());
        w.number(v.getY());
        w.number(v.getZ());
        w.text(a.getNameTag());
        w.byte(a.isOnGround());
        w.byte(a.isInWater());
        w.byte(a.isInLava());
        auto *mob = a.asMob();
        w.byte(mob != nullptr);
        if (mob) {
            w.integer(mob->getHealth());
        }
        auto *p = a.asPlayer();
        w.wide(p ? playerId(*c, *p) : 0);
        break;
    }
    case 32: {
        auto &a = entity(*c, s, token);
        int kind = r.integer();
        auto &ref = s.entities.at(token);
        if (kind == 0) {
            auto to = location(*c, s, ref.world, r);
            r.done();
            w.byte(a.teleport(to));
        }
        else if (kind == 1) {
            auto name = r.text();
            r.done();
            a.setNameTag(name);
            w.byte(true);
        }
        else if (kind == 2) {
            r.done();
            if (a.asPlayer()) {
                throw std::runtime_error("Use Player.kick for player entities");
            }
            ref.valid = false;
            a.remove();
            w.byte(true);
        }
        else if (kind == 3) {
            int health = r.integer();
            r.done();
            auto *mob = a.asMob();
            if (!mob || health < 0 || health > mob->getMaxHealth()) {
                throw std::runtime_error("Invalid mob health");
            }
            mob->setHealth(health);
            w.byte(true);
        }
        else if (kind == 4) {
            double yaw = r.number(), pitch = r.number();
            r.done();
            if (std::abs(yaw) > std::numeric_limits<float>::max() ||
                std::abs(pitch) > std::numeric_limits<float>::max()) {
                throw std::runtime_error("Invalid rotation");
            }
            a.setRotation(static_cast<float>(yaw), static_cast<float>(pitch));
            w.byte(true);
        }
        else {
            throw std::runtime_error("Invalid entity action");
        }
        break;
    }
    case 33: {
        auto name = r.text();
        auto at = location(*c, s, token, r);
        auto type = r.text();
        r.done();
        if (name != at.getDimension().getName() || !identifier(type)) {
            throw std::runtime_error("Invalid spawn request");
        }
        auto *a = at.getDimension().spawnActor(at, type);
        w.wide(a ? entityId(s, token, *a) : 0);
        break;
    }
    case 34: {
        r.done();
        auto &p = player(*c, token);
        auto *level = c->server->getLevel();
        if (!level) {
            throw std::runtime_error("World unavailable");
        }
        w.wide(entityId(s, worldId(s, *level), p));
        break;
    }
    case 35:
        r.done();
        w.byte(resolveEntity(*c, s, token) != nullptr);
        break;
    default:
        throw std::runtime_error("Unknown advanced operation");
    }
}

void responseValue(Writer &w, const Json &v)
{
    if (v.is_string()) {
        w.byte(0);
        w.text(v.get<std::string>());
    }
    else if (v.is_number_integer()) {
        w.byte(1);
        w.integer(v.get<int>());
    }
    else if (v.is_boolean()) {
        w.byte(2);
        w.byte(v.get<bool>());
    }
    else if (v.is_number()) {
        w.byte(3);
        w.number(v.get<double>());
    }
    else {
        throw std::runtime_error("Invalid form response value");
    }
}
void finish(const std::shared_ptr<Context> &c, jlong token, int status, const Json &values)
{
    if (!c->advanced) {
        return;
    }
    auto it = c->advanced->forms.find(token);
    if (it == c->advanced->forms.end()) {
        return;
    }
    auto entry = std::move(it->second);
    c->advanced->forms.erase(it);
    try {
        Writer out;
        out.integer(status);
        out.integer(static_cast<int>(values.size()));
        for (const auto &v : values) {
            responseValue(out, v);
        }
        Env env;
        auto data = bytes(env.e, out);
        env.e->CallVoidMethod(entry.callback->value, entry.method, data);
        callbackCheck(env.e);
    }
    catch (const std::exception &error) {
        c->logger->error("Java form completion failed: {}", error.what());
    }
}
bool validResponse(const Json &schema, const Json &values)
{
    if (!values.is_array()) {
        return false;
    }
    int kind = schema.at("kind");
    auto choice = [](const Json &value, int count) {
        // Check before narrowing: a malicious 64-bit index must not wrap into
        // a valid 32-bit button/dropdown index.
        return value.is_number_integer() && value.get<double>() >= 0 && value.get<double>() < count;
    };
    if (kind < 2) {
        return values.size() == 1 && choice(values[0], schema.at("choices").get<int>());
    }
    auto &controls = schema.at("controls");
    if (values.size() != controls.size()) {
        return false;
    }
    for (std::size_t i = 0; i < values.size(); ++i) {
        const auto &v = values[i];
        const auto &control = controls[i];
        int type = control.at("kind");
        if (type == 0 && (!v.is_string() || v.get_ref<const std::string &>().size() > 1024 * 1024)) {
            return false;
        }
        if (type == 1 && !v.is_boolean()) {
            return false;
        }
        if (type == 2) {
            if (!v.is_number()) {
                return false;
            }
            double n = v.get<double>(), min = control.at("min"), max = control.at("max"), step = control.at("step");
            if (!std::isfinite(n) || n < min || n > max) {
                return false;
            }
            double tick = (n - min) / step;
            if (std::abs(tick - std::round(tick)) > 0.0001 && n != max) {
                return false;
            }
        }
        if (type >= 3 && !choice(v, control.at("choices").get<int>())) {
            return false;
        }
    }
    return true;
}
void submitted(const std::weak_ptr<Context> &weak, jlong token, Player *p, const Json &values, int closed = -1) noexcept
{
    try {
        auto c = weak.lock();
        if (!c || !c->active || c->main.load() != std::this_thread::get_id() || !c->advanced) {
            return;
        }
        auto it = c->advanced->forms.find(token);
        if (it == c->advanced->forms.end()) {
            return;
        }
        if (!p || p->getUniqueId() != it->second.player || c->server->getPlayer(it->second.player) != p) {
            return;
        }
        if (closed >= 0) {
            finish(c, token, closed);
            return;
        }
        finish(c, token, validResponse(it->second.schema, values) ? 0 : 4,
               validResponse(it->second.schema, values) ? values : Json::array());
    }
    catch (...) {
        if (auto c = weak.lock()) {
            finish(c, token, 4);
        }
    }
}
jlong send(const std::shared_ptr<Context> &c, JNIEnv *e, jlong playerToken, Reader &r, jobject callback)
{
    if (!callback) {
        throw std::invalid_argument("Null form callback");
    }
    auto &p = player(*c, playerToken);
    if (p.isDead()) {
        throw std::runtime_error("Cannot send form to dead player");
    }
    auto &s = state(c);
    auto pinned = c->advanced;
    if (s.disconnected.contains(p.getUniqueId())) {
        throw std::runtime_error("Player is disconnecting; forms are closed");
    }
    if (s.forms.size() >= 1024) {
        throw std::runtime_error("Too many pending forms for this plugin");
    }
    std::size_t playerForms = 0;
    for (const auto &[id, form] : s.forms) {
        if (form.player == p.getUniqueId()) {
            ++playerForms;
        }
    }
    if (playerForms >= 32) {
        throw std::runtime_error("Too many pending forms for this player/plugin");
    }
    auto cls = e->GetObjectClass(callback);
    checked(e);
    auto method = e->GetMethodID(cls, "complete", "([B)V");
    checked(e);
    auto token = id();
    std::weak_ptr<Context> weak = c;
    int kind = r.integer();
    auto title = r.text();
    Json schema{{"kind", kind}};
    std::variant<ActionForm, MessageForm, ModalForm> form;
    if (kind == 0) {
        ActionForm f;
        f.setTitle(title).setContent(r.text());
        int count = r.count(256);
        if (count < 1) {
            throw std::runtime_error("Empty action form");
        }
        schema["choices"] = count;
        for (int i = 0; i < count; ++i) {
            auto label = r.text();
            std::optional<std::string> image;
            if (r.boolean()) {
                image = r.text();
            }
            f.addButton(label, image);
        }
        f.setOnSubmit([weak, token](Player *p, int choice) { submitted(weak, token, p, Json::array({choice})); });
        f.setOnClose([weak, token](Player *p) { submitted(weak, token, p, Json::array(), 1); });
        form = std::move(f);
    }
    else if (kind == 1) {
        MessageForm f;
        f.setTitle(title).setContent(r.text());
        auto first = r.text(), second = r.text();
        f.setButton1(first).setButton2(second);
        schema["choices"] = 2;
        f.setOnSubmit([weak, token](Player *p, int choice) { submitted(weak, token, p, Json::array({choice})); });
        f.setOnClose([weak, token](Player *p) { submitted(weak, token, p, Json::array(), 1); });
        form = std::move(f);
    }
    else if (kind == 2) {
        ModalForm f;
        f.setTitle(title);
        schema["controls"] = Json::array();
        int count = r.count(256);
        for (int i = 0; i < count; ++i) {
            int type = r.integer();
            auto label = r.text();
            Json constraint{{"kind", type}};
            if (type == 0) {
                auto placeholder = r.text(), value = r.text();
                f.addControl(TextInput(label, placeholder, value));
            }
            else if (type == 1) {
                f.addControl(Toggle(label, r.boolean()));
            }
            else if (type == 2) {
                double min = r.number(), max = r.number(), step = r.number(), value = r.number();
                if (min > max || step <= 0 || value < min || value > max ||
                    std::abs(min) > std::numeric_limits<float>::max() ||
                    std::abs(max) > std::numeric_limits<float>::max() || step > std::numeric_limits<float>::max()) {
                    throw std::runtime_error("Invalid slider");
                }
                f.addControl(Slider(label, static_cast<float>(min), static_cast<float>(max), static_cast<float>(step),
                                    static_cast<float>(value)));
                constraint["min"] = min;
                constraint["max"] = max;
                constraint["step"] = step;
            }
            else if (type == 3 || type == 4) {
                int options = r.count(256);
                if (options < 1) {
                    throw std::runtime_error("Empty selection control");
                }
                std::vector<std::string> values;
                for (int j = 0; j < options; ++j) {
                    values.push_back(r.text());
                }
                int selected = r.integer();
                if (selected < 0 || selected >= options) {
                    throw std::runtime_error("Invalid default index");
                }
                if (type == 3) {
                    f.addControl(Dropdown(label, values, selected));
                }
                else {
                    f.addControl(StepSlider(label, values, selected));
                }
                constraint["choices"] = options;
            }
            else {
                throw std::runtime_error("Unknown modal control");
            }
            schema["controls"].push_back(constraint);
        }
        f.setOnSubmit([weak, token](Player *p, std::string response) {
            if (response.size() > 1024 * 1024) {
                submitted(weak, token, p, Json());
                return;
            }
            auto values = Json::parse(response, nullptr, false);
            submitted(weak, token, p, values);
        });
        f.setOnClose([weak, token](Player *p) { submitted(weak, token, p, Json::array(), 1); });
        form = std::move(f);
    }
    else {
        throw std::runtime_error("Unsupported form kind");
    }
    r.done();
    s.forms.emplace(token, FormState{p.getUniqueId(), std::make_shared<Ref>(e, callback), method, schema});
    try {
        std::visit([&](auto &&f) { p.sendForm(std::forward<decltype(f)>(f)); }, std::move(form));
    }
    catch (...) {
        s.forms.erase(token);
        throw;
    }
    return token;
}
}  // namespace

void closeAdvanced(Context &c)
{
    if (!c.advanced) {
        return;
    }
    // Context is already invalidated by closeGameplay. Complete futures while refs are pinned,
    // but do not close all player forms: that would also close other plugins' requests.
    auto borrowed = std::shared_ptr<Context>(&c, [](Context *) {});
    while (!c.advanced->forms.empty()) {
        finish(borrowed, c.advanced->forms.begin()->first, 3);
    }
    for (auto *handler : {c.advanced->quit, c.advanced->remove, c.advanced->join}) {
        if (!handler) {
            continue;
        }
        try {
            manager(c).unregisterManagedEvent(*handler);
        }
        catch (const std::exception &error) {
            c.logger->error("Advanced listener cleanup failed: {}", error.what());
        }
    }
    c.advanced.reset();
}
}  // namespace endstone::core::java::details

namespace endstone::core::java {
using namespace details;
namespace {
jbyteArray JNICALL advanced(JNIEnv *e, jclass, jlong owner, jint op, jlong handle, jbyteArray payload)
{
    return boundary(e, [&] {
        auto c = context(owner);
        Reader request(e, payload);
        Writer response;
        advancedOperation(c, op, handle, request, response);
        return bytes(e, response);
    });
}
jlong JNICALL showForm(JNIEnv *e, jclass, jlong owner, jlong player, jbyteArray payload, jobject callback)
{
    return boundary(e, [&] {
        auto c = context(owner, true);
        Reader request(e, payload);
        return send(c, e, player, request, callback);
    });
}
void JNICALL cancelForm(JNIEnv *e, jclass, jlong owner, jlong request)
{
    boundary(e, [&] {
        auto c = context(owner);
        finish(c, request, 5);
    });
}
}  // namespace
void registerAdvanced(JNIEnv *e)
{
    auto cls = e->FindClass("dev/zernyth/runtime/Native");
    callbackCheck(e);
    JNINativeMethod methods[] = {
        {const_cast<char *>("advanced"), const_cast<char *>("(JIJ[B)[B"), reinterpret_cast<void *>(advanced)},
        {const_cast<char *>("showForm"), const_cast<char *>("(JJ[BLdev/zernyth/runtime/Native$FormCallback;)J"),
         reinterpret_cast<void *>(showForm)},
        {const_cast<char *>("cancelForm"), const_cast<char *>("(JJ)V"), reinterpret_cast<void *>(cancelForm)}};
    if (e->RegisterNatives(cls, methods, 3) != JNI_OK) {
        callbackCheck(e);
        throw std::runtime_error("Advanced RegisterNatives failed");
    }
}
}  // namespace endstone::core::java
