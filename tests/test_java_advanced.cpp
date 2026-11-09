#include <fstream>
#include <thread>

#include "endstone/core/logger_factory.h"
#include "endstone/core/plugin/java_plugin_loader.h"
#include "endstone/core/plugin/plugin_manager.h"
#include "endstone/core/plugin/service_manager.h"
#include "endstone/core/scheduler/scheduler.h"
#include "endstone/event/actor/actor_remove_event.h"
#include "endstone/event/player/player_join_event.h"
#include "endstone/event/player/player_quit_event.h"
#include "endstone/plugin/plugin.h"
#include "java_command_map.h"
#include "java_item_test_factory.h"
#include "mocks.h"

using namespace endstone;
using namespace endstone::core;
using testing::_;
using testing::Return;
using testing::ReturnRef;

namespace {
class ItemRegistry final : public Registry<ItemType> {
public:
    ItemType &entry;
    explicit ItemRegistry(ItemType &e) : entry(e) {}
    ItemType *get(ItemTypeId id) noexcept override { return id == entry.getId() ? &entry : nullptr; }
    const ItemType *get(ItemTypeId id) const noexcept override { return id == entry.getId() ? &entry : nullptr; }
    void forEach(std::function<bool(const ItemType &)> visitor) const override { visitor(entry); }
};
class Data final : public BlockData {
public:
    std::string type;
    BlockStates states;
    Data(std::string type, BlockStates states = {}) : type(std::move(type)), states(std::move(states)) {}
    std::string getType() const override { return type; }
    BlockStates getBlockStates() const override { return states; }
    std::uint32_t getRuntimeId() const override { return 1; }
};
}  // namespace

TEST(JavaAdvancedTest, InventoryItemsWorldEntitiesFormsAndCleanup)
{
    auto jar = std::getenv("ZERNYTH_ADVANCED_TEST_JAR");
    auto peerJar = std::getenv("ZERNYTH_ADVANCED_PEER_TEST_JAR");
    if (!jar || !peerJar || !std::getenv("ZERNYTH_JVM_LIBRARY") || !std::getenv("ZERNYTH_JAVA_CLASSPATH")) {
        GTEST_SKIP() << "Set ZERNYTH_ADVANCED_TEST_JAR and JVM/classpath";
    }
    testing::NiceMock<MockServer> server;
    testing::NiceMock<GameplayMockPlayer> player, actor, stranger;
    testing::NiceMock<GameplayMockConsole> console;
    testing::NiceMock<GameplayMockDimension> dim;
    testing::NiceMock<GameplayMockLevel> level;
    testing::NiceMock<AdvancedMockInventory> inv;
    testing::NiceMock<AdvancedMockType> type;
    testing::NiceMock<AdvancedMockEnchant> enchant;
    ItemRegistry registry(type);
    EndstoneScheduler scheduler(server);
    EndstoneServiceManager services;
    GameplayCommands commands;
    EndstonePluginManager manager(server);
    UUID uuid, foreign;
    foreign.data[0] = 1;
    Level *current = &level;
    bool chunks = true, online = true, alive = true;
    std::uint64_t runtime = 420;
    int health = 15, blockReads = 0;
    std::string blockType = "minecraft:stone";
    std::string actorName;
    std::vector<std::optional<ItemStack>> slots(4);
    using FormVariant = std::variant<MessageForm, ActionForm, ModalForm>;
    std::vector<FormVariant> forms;
    std::filesystem::remove("advanced-forms-disabled.marker");
    ON_CALL(server, isPrimaryThread()).WillByDefault(Return(true));
    ON_CALL(server, getLogger()).WillByDefault(ReturnRef(LoggerFactory::getLogger("AdvancedTest")));
    ON_CALL(server, getPluginManager()).WillByDefault(ReturnRef(manager));
    ON_CALL(server, getScheduler()).WillByDefault(ReturnRef(scheduler));
    ON_CALL(server, getServiceManager()).WillByDefault(ReturnRef(services));
    ON_CALL(server, getLevel()).WillByDefault([&] { return current; });
    ON_CALL(server, getOnlinePlayers()).WillByDefault(Return(std::vector<Player *>{&player}));
    ON_CALL(server, getPlayer(testing::An<UUID>())).WillByDefault([&](UUID id) -> Player * {
        return online && id == uuid ? &player : nullptr;
    });
    ON_CALL(server, _getRegistry(_)).WillByDefault([&](const std::string &name) -> IRegistry * {
        return name == ItemType::RegistryType ? &registry : nullptr;
    });
    ON_CALL(console, getName()).WillByDefault(Return("Console"));
    ON_CALL(console, asConsole()).WillByDefault(Return(&console));
    ON_CALL(type, getId()).WillByDefault(Return(ItemTypeId("zernyth:test_item")));
    ON_CALL(type, getMaxStackSize()).WillByDefault(Return(64));
    ON_CALL(type, getMaxDurability()).WillByDefault(Return(100));
    ON_CALL(enchant, getId()).WillByDefault(Return(EnchantmentId("minecraft:sharpness")));
    ON_CALL(enchant, getMaxLevel()).WillByDefault(Return(3));
    ON_CALL(type, createItemStack(testing::An<int>())).WillByDefault([&](int n) {
        return ItemStackTestFactory::create(type, n, &enchant);
    });
    EXPECT_CALL(type, createItemStack(testing::An<int>())).Times(testing::AtLeast(1));
    EXPECT_CALL(type, createItemStack(65)).Times(0);
    ON_CALL(player, getName()).WillByDefault(Return("Alex"));
    ON_CALL(player, getUniqueId()).WillByDefault(Return(uuid));
    ON_CALL(stranger, getUniqueId()).WillByDefault(Return(foreign));
    ON_CALL(player, getInventory()).WillByDefault(ReturnRef(inv));
    ON_CALL(player, getEnderChest()).WillByDefault(ReturnRef(inv));
    ON_CALL(player, getId()).WillByDefault(Return(1));
    ON_CALL(player, getRuntimeId()).WillByDefault(Return(10));
    ON_CALL(player, isValid()).WillByDefault(Return(true));
    ON_CALL(player, asPlayer()).WillByDefault(Return(&player));
    ON_CALL(player, asMob()).WillByDefault(Return(&player));
    ON_CALL(player, getLocation()).WillByDefault(Return(Location(dim, 1, 2, 3)));
    ON_CALL(player, getType()).WillByDefault(Return("minecraft:player"));
    EXPECT_CALL(player, closeForm()).Times(0);
    ON_CALL(player, sendForm(_)).WillByDefault([&](FormVariant form) { forms.push_back(std::move(form)); });
    ON_CALL(inv, getSize()).WillByDefault(Return(4));
    ON_CALL(inv, getMaxStackSize()).WillByDefault(Return(64));
    ON_CALL(inv, getItem(_)).WillByDefault([&](int index) { return slots.at(index); });
    EXPECT_CALL(inv, getItem(_)).Times(testing::AnyNumber());
    EXPECT_CALL(inv, getItem(testing::Lt(0))).Times(0);
    EXPECT_CALL(inv, getItem(testing::Ge(4))).Times(0);
    ON_CALL(inv, setItem(_, _)).WillByDefault([&](int index, std::optional<ItemStack> item) {
        slots.at(index) = std::move(item);
    });
    ON_CALL(inv, getContents()).WillByDefault([&] { return slots; });
    ON_CALL(inv, setContents(_)).WillByDefault([&](std::vector<std::optional<ItemStack>> items) {
        slots.assign(4, std::nullopt);
        std::copy(items.begin(), items.end(), slots.begin());
    });
    ON_CALL(inv, clear()).WillByDefault([&] { slots.assign(4, std::nullopt); });
    ON_CALL(inv, clear(testing::An<int>())).WillByDefault([&](int index) { slots.at(index).reset(); });
    ON_CALL(inv, contains(testing::An<const ItemStack &>(), _)).WillByDefault([&](const ItemStack &item, int count) {
        int total = 0;
        for (auto &slot : slots) {
            if (slot && slot->isSimilar(item)) {
                total += slot->getAmount();
            }
        }
        return total >= count;
    });
    ON_CALL(inv, addItem(testing::An<std::vector<ItemStack>>())).WillByDefault([&](std::vector<ItemStack> items) {
        std::unordered_map<int, ItemStack> left;
        for (std::size_t i = 0; i < items.size(); i++) {
            auto empty = std::find(slots.begin(), slots.end(), std::nullopt);
            if (empty == slots.end()) {
                left.emplace(static_cast<int>(i), items[i]);
            }
            else {
                *empty = items[i];
            }
        }
        return left;
    });
    ON_CALL(inv, removeItem(testing::An<std::vector<ItemStack>>())).WillByDefault([&](std::vector<ItemStack> items) {
        for (auto &item : items) {
            for (auto &slot : slots) {
                if (slot && *slot == item) {
                    slot.reset();
                    break;
                }
            }
        }
        return std::unordered_map<int, ItemStack>{};
    });
    ON_CALL(level, getName()).WillByDefault(Return("test-world"));
    ON_CALL(level, getDimensions()).WillByDefault(Return(std::vector<Dimension *>{&dim}));
    ON_CALL(level, getDimension("overworld")).WillByDefault(Return(&dim));
    ON_CALL(dim, getName()).WillByDefault(Return("overworld"));
    ON_CALL(dim, getLoadedChunks()).WillByDefault([&] {
        std::vector<std::unique_ptr<Chunk>> out;
        if (chunks) {
            auto chunk = std::make_unique<testing::NiceMock<AdvancedMockChunk>>();
            ON_CALL(*chunk, getX()).WillByDefault(Return(0));
            ON_CALL(*chunk, getZ()).WillByDefault(Return(0));
            out.push_back(std::move(chunk));
        }
        return out;
    });
    ON_CALL(server, createBlockData(_, _))
        .WillByDefault([](std::string type, const BlockStates &states) -> std::unique_ptr<BlockData> {
            return std::make_unique<Data>(type, states);
        });
    std::function<std::unique_ptr<Block>()> makeBlock = [&] {
        auto block = std::make_unique<testing::NiceMock<GameplayMockBlock>>();
        ON_CALL(*block, getData()).WillByDefault([&]() -> std::unique_ptr<BlockData> {
            return std::make_unique<Data>(blockType);
        });
        ON_CALL(*block, setData(_, _)).WillByDefault([&](const BlockData &data, bool) { blockType = data.getType(); });
        ON_CALL(*block, captureState()).WillByDefault([&] {
            auto state = std::make_unique<testing::NiceMock<AdvancedMockBlockState>>();
            auto captured = std::make_shared<std::string>(blockType);
            ON_CALL(*state, getData()).WillByDefault([captured]() -> std::unique_ptr<BlockData> {
                return std::make_unique<Data>(*captured);
            });
            ON_CALL(*state, setData(_)).WillByDefault([captured](const BlockData &data) {
                *captured = data.getType();
            });
            ON_CALL(*state, update(_, _)).WillByDefault([&, captured](bool, bool) {
                blockType = *captured;
                return true;
            });
            return state;
        });
        return block;
    };
    ON_CALL(dim, getBlockAt(testing::An<int>(), _, _)).WillByDefault([&](int, int, int) {
        ++blockReads;
        return makeBlock();
    });
    ON_CALL(level, getActors()).WillByDefault(Return(std::vector<Actor *>{&player, &actor}));
    ON_CALL(actor, getId()).WillByDefault(Return(42));
    ON_CALL(actor, getRuntimeId()).WillByDefault([&] { return runtime; });
    ON_CALL(actor, isValid()).WillByDefault([&] { return alive; });
    ON_CALL(actor, getType()).WillByDefault(Return("minecraft:pig"));
    ON_CALL(actor, asMob()).WillByDefault(Return(&actor));
    ON_CALL(actor, getLocation()).WillByDefault(Return(Location(dim, 1, 2, 3, 10, 20)));
    ON_CALL(actor, getVelocity()).WillByDefault(Return(Vector(1, 2, 3)));
    ON_CALL(actor, getHealth()).WillByDefault([&] { return health; });
    ON_CALL(actor, getMaxHealth()).WillByDefault(Return(20));
    EXPECT_CALL(actor, setHealth(7)).WillOnce([&](int value) { health = value; });
    EXPECT_CALL(actor, setRotation(90, 45)).Times(1);
    ON_CALL(actor, getNameTag()).WillByDefault([&] { return actorName; });
    EXPECT_CALL(actor, setNameTag("Pig")).WillOnce([&](std::string name) { actorName = std::move(name); });
    EXPECT_CALL(actor, teleport(testing::An<const Location &>())).WillOnce(Return(true));
    EXPECT_CALL(actor, remove()).WillOnce([&] { alive = false; });
    EXPECT_CALL(dim, spawnActor(_, "minecraft:pig")).WillOnce(Return(&actor));
    manager.registerLoader(std::make_unique<JavaPluginLoader>(
        server, &commands, [&](const std::shared_ptr<Command> &c) { commands.entries.erase(c->getName()); }));
    auto *plugin = manager.loadPlugin(jar);
    ASSERT_NE(plugin, nullptr);
    manager.enablePlugin(*plugin);
    ASSERT_TRUE(plugin->isEnabled());
    auto phase = [&](const std::string &name) {
        ASSERT_TRUE(commands.dispatch(console, "advanced " + name)) << name;
    };
    phase("init");
    ASSERT_TRUE(slots[0]);
    EXPECT_EQ(slots[0]->getNbt().at("unknown_plugin_data").get<StringTag>().value(), "preserve-me");
    EXPECT_TRUE(slots[0]->getNbt() == ItemStackTestFactory::create(type, 1, &enchant).getNbt());
    phase("invalid");
    for (int tick = 0; tick < 5; ++tick) {
        scheduler.mainThreadHeartbeat(tick);
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }
    phase("async");
    chunks = false;
    int readsBefore = blockReads;
    phase("unloaded");
    EXPECT_EQ(blockReads, readsBefore);
    chunks = true;
    runtime = 419;
    phase("entity-removed");
    runtime = 420;
    phase("entity-removed");
    phase("entity-refresh");
    ActorRemoveEvent removed(actor);
    manager.callEvent(removed);
    alive = false;
    phase("entity-removed");
    runtime = 421;
    alive = true;
    phase("entity-reused");
    phase("remove");
    current = nullptr;
    phase("world-off");
    current = &level;
    phase("world-back");
    phase("forms");
    ASSERT_EQ(forms.size(), 5);
    std::get<ActionForm>(forms[0]).getOnSubmit()(&stranger,
                                                 0);  // Foreign response must not complete another player's future.
    std::thread wrongThread([&] { std::get<ActionForm>(forms[0]).getOnSubmit()(&player, 0); });
    wrongThread.join();
    phase("foreign-pending");
    std::get<ActionForm>(forms[0]).getOnSubmit()(&player, 99);
    std::get<ActionForm>(forms[0]).getOnSubmit()(&player, 0);  // Completed request is one-shot.
    std::get<MessageForm>(forms[1]).getOnSubmit()(&player, 0);
    std::get<ModalForm>(forms[2]).getOnSubmit()(&player, "[\"value\",true,999,1,0]");
    std::get<ModalForm>(forms[3]).getOnSubmit()(&player, "[\"value\",true,1.5,1,0]");
    std::get<ModalForm>(forms[4]).getOnSubmit()(&player, "not-json");
    phase("forms-check");
    ASSERT_EQ(forms.size(), 7);
    std::get<ActionForm>(forms[5]).getOnSubmit()(&player, 0);  // Cancelled callback no longer retained.
    std::get<ActionForm>(forms[6]).getOnClose()(&player);
    phase("closed");
    PlayerQuitEvent quit(player, std::nullopt);
    manager.callEvent(quit);
    online = false;
    phase("disconnect");
    online = true;
    PlayerJoinEvent rejoin(player, std::nullopt);
    manager.callEvent(rejoin);
    phase("overflow-form");
    std::get<ModalForm>(forms.back()).getOnSubmit()(&player, "[\"value\",true,1.5,4294967296,0]");
    phase("overflow-check");
    phase("pending");
    auto last = forms.back();
    auto *peer = manager.loadPlugin(peerJar);
    ASSERT_NE(peer, nullptr);
    manager.enablePlugin(*peer);
    ASSERT_TRUE(peer->isEnabled());
    ASSERT_TRUE(commands.dispatch(console, "advanced_peer peer-form"));
    auto peerForm = forms.back();
    manager.disablePlugin(*plugin);
    EXPECT_FALSE(plugin->isEnabled());
    std::ifstream marker("advanced-forms-disabled.marker");
    std::string status;
    marker >> status;
    EXPECT_EQ(status, "PLUGIN_DISABLED");
    marker.close();
    std::filesystem::remove("advanced-forms-disabled.marker");
    std::get<ActionForm>(last).getOnSubmit()(&player, 0);  // Late native response cannot call a disabled plugin.
    EXPECT_TRUE(commands.dispatch(console, "advanced_peer peer-pending"));
    std::get<ActionForm>(peerForm).getOnSubmit()(&player, 0);
    EXPECT_TRUE(commands.dispatch(console, "advanced_peer peer-submitted"));
    EXPECT_FALSE(commands.getCommand("advanced"));
    manager.clearPlugins();
}
