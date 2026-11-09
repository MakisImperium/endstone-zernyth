#include <chrono>
#include <cstdlib>
#include <sstream>
#include <thread>

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "endstone/core/logger_factory.h"
#include "endstone/core/plugin/java_plugin_loader.h"
#include "endstone/core/plugin/plugin_manager.h"
#include "endstone/core/plugin/service_manager.h"
#include "endstone/core/scheduler/scheduler.h"
#include "endstone/event/block/block_break_event.h"
#include "endstone/event/block/block_place_event.h"
#include "endstone/event/player/player_chat_event.h"
#include "endstone/event/player/player_interact_event.h"
#include "endstone/event/player/player_join_event.h"
#include "endstone/event/player/player_move_event.h"
#include "endstone/event/player/player_quit_event.h"
#include "endstone/plugin/plugin.h"
#include "java_gameplay_mocks.h"
#include "mocks.h"

using namespace endstone;
using namespace endstone::core;
using testing::_;
using testing::Return;
using testing::ReturnRef;

#include "java_command_map.h"

TEST(JavaGameplayTest, PlayerEventsCommandsSchedulerAndCleanup)
{
    auto jar = std::getenv("ZERNYTH_GAMEPLAY_TEST_JAR");
    auto failureJar = std::getenv("ZERNYTH_GAMEPLAY_FAILURE_TEST_JAR");
    if (!jar || !failureJar || !std::getenv("ZERNYTH_JVM_LIBRARY") || !std::getenv("ZERNYTH_JAVA_CLASSPATH")) {
        GTEST_SKIP() << "Set ZERNYTH_GAMEPLAY_TEST_JAR and JVM/classpath variables";
    }
    testing::NiceMock<MockServer> server;
    testing::NiceMock<GameplayMockPlayer> player;
    testing::NiceMock<GameplayMockDimension> dimension;
    testing::NiceMock<GameplayMockLevel> level;
    testing::NiceMock<GameplayMockConsole> console;
    EndstoneScheduler scheduler(server);
    EndstoneServiceManager services;
    GameplayCommands commands;
    EndstonePluginManager manager(server);
    UUID uuid;
    bool online = true, permitted = true;
    ON_CALL(server, isPrimaryThread()).WillByDefault(Return(true));
    ON_CALL(server, getLogger()).WillByDefault(ReturnRef(LoggerFactory::getLogger("GameplayTest")));
    ON_CALL(server, getPluginManager()).WillByDefault(ReturnRef(manager));
    ON_CALL(server, getScheduler()).WillByDefault(ReturnRef(scheduler));
    ON_CALL(server, getServiceManager()).WillByDefault(ReturnRef(services));
    ON_CALL(server, getOnlinePlayers()).WillByDefault(Return(std::vector<Player *>{&player}));
    ON_CALL(server, getPlayer(testing::An<UUID>())).WillByDefault([&](UUID) -> Player * {
        return online ? &player : nullptr;
    });
    ON_CALL(server, getLevel()).WillByDefault(Return(&level));
    ON_CALL(level, getDimension("overworld")).WillByDefault(Return(&dimension));
    ON_CALL(dimension, getName()).WillByDefault(Return("overworld"));
    ON_CALL(player, getUniqueId()).WillByDefault(Return(uuid));
    ON_CALL(player, getName()).WillByDefault(Return("Alex"));
    ON_CALL(player, getHealth()).WillByDefault(Return(20));
    ON_CALL(player, getGameMode()).WillByDefault(Return(GameMode::Survival));
    ON_CALL(player, getServer()).WillByDefault(ReturnRef(server));
    ON_CALL(player, getLocation()).WillByDefault(Return(Location(dimension, 1, 2, 3, 10, 20)));
    ON_CALL(player, asPlayer()).WillByDefault(Return(&player));
    ON_CALL(console, asConsole()).WillByDefault(Return(&console));
    ON_CALL(console, getName()).WillByDefault(Return("Console"));
    ON_CALL(console, hasPermission(testing::An<std::string>())).WillByDefault(Return(true));
    EXPECT_CALL(console, sendMessage(_)).Times(1);
    ON_CALL(player, hasPermission(testing::An<std::string>())).WillByDefault([&](std::string value) {
        return permitted && value == "gameplay.use";
    });
    EXPECT_CALL(player, sendMessage(_)).Times(testing::AtLeast(2));
    EXPECT_CALL(player, kick("fixture kick")).Times(1);
    EXPECT_CALL(player, setGameMode(GameMode::Creative)).Times(1);
    EXPECT_CALL(player, teleport(testing::An<const Location &>())).WillOnce([](const Location &to) {
        EXPECT_EQ(to.getX(), 4);
        EXPECT_EQ(to.getY(), 5);
        EXPECT_EQ(to.getZ(), 6);
        EXPECT_EQ(to.getPitch(), 30);
        EXPECT_EQ(to.getYaw(), 40);
        return true;
    });
    manager.registerLoader(
        std::make_unique<JavaPluginLoader>(server, &commands, [&](const std::shared_ptr<Command> &c) {
            auto it = commands.entries.find(c->getName());
            if (it != commands.entries.end() && it->second == c) {
                commands.entries.erase(it);
            }
        }));
    Plugin *plugin = nullptr;
    // Model BDS's Main -> Server lifecycle handover, without starting BDS.
    std::thread loading([&] { plugin = manager.loadPlugin(jar); });
    loading.join();
    ASSERT_NE(plugin, nullptr);
    manager.enablePlugin(*plugin);
    ASSERT_TRUE(plugin->isEnabled());
    ASSERT_TRUE(commands.getCommand("gameplay"));
    EXPECT_FALSE(commands.getCommand("temporary"));
    EXPECT_TRUE(commands.getCommand("gameplay")->execute(player, {"arguments", "one", "two words"}));
    EXPECT_TRUE(commands.dispatch(player, "gameplay"));
    EXPECT_TRUE(commands.dispatch(console, "gameplay console"));
    permitted = false;
    EXPECT_TRUE(commands.dispatch(player, "gameplay throw"));  // Permission denial suppresses the throwing executor.
    permitted = true;
    EXPECT_FALSE(commands.dispatch(player, "gameplay throw"));  // Isolated Java exception.

    PlayerJoinEvent join(player, std::nullopt);
    manager.callEvent(join);
    PlayerQuitEvent quit(player, std::nullopt);
    manager.callEvent(quit);
    PlayerChatEvent chat(player, "original", std::nullopt);
    manager.callEvent(chat);
    EXPECT_TRUE(chat.isCancelled());
    EXPECT_EQ(chat.getMessage(), "modified \xf0\x9f\x98\x80");
    PlayerMoveEvent move(player, Location(dimension, 1, 2, 3), Location(dimension, 4, 5, 6));
    manager.callEvent(move);
    EXPECT_TRUE(move.isCancelled());
    PlayerInteractEvent interact(player, PlayerInteractEvent::Action::RightClickAir, std::nullopt, nullptr,
                                 BlockFace::Up, std::nullopt);
    manager.callEvent(interact);
    EXPECT_TRUE(interact.isCancelled());
    auto block = [&] {
        auto b = std::make_unique<testing::NiceMock<GameplayMockBlock>>();
        ON_CALL(*b, getLocation()).WillByDefault(Return(Location(dimension, 1, 2, 3)));
        return b;
    };
    BlockBreakEvent broken(block(), player);
    manager.callEvent(broken);
    EXPECT_TRUE(broken.isCancelled());
    BlockPlaceEvent placed(nullptr, block(), block(), player);
    manager.callEvent(placed);
    EXPECT_TRUE(placed.isCancelled());
    EXPECT_TRUE(commands.dispatch(player, "gameplay events"));

    for (int tick = 0; tick < 10; ++tick) {
        scheduler.mainThreadHeartbeat(tick);
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }
    EXPECT_TRUE(commands.dispatch(player, "gameplay tasks"));
    online = false;
    EXPECT_TRUE(commands.dispatch(console, "gameplay offline"));
    online = true;
    EXPECT_TRUE(commands.dispatch(player, "gameplay unregister"));
    PlayerChatEvent after(player, "original", std::nullopt);
    manager.callEvent(after);
    EXPECT_FALSE(after.isCancelled());
    EXPECT_EQ(after.getMessage(), "original");
    EXPECT_FALSE(manager.isEventRegistered<PlayerChatEvent>());
    PlayerJoinEvent secondJoin(player, std::nullopt);
    manager.callEvent(secondJoin);
    EXPECT_FALSE(manager.isEventRegistered<PlayerJoinEvent>());
    auto queued = scheduler.runTaskLater(*plugin, [] {}, 1000);
    ASSERT_NE(queued, nullptr);
    auto retainedCommand = commands.getCommand("gameplay");
    manager.disablePlugin(*plugin);
    EXPECT_FALSE(plugin->isEnabled());
    EXPECT_TRUE(queued->isCancelled());
    EXPECT_FALSE(retainedCommand->execute(player, {}));
    EXPECT_FALSE(commands.getCommand("gameplay"));
    EXPECT_FALSE(manager.isEventRegistered<PlayerJoinEvent>());
    auto *failed = manager.loadPlugin(failureJar);
    ASSERT_NE(failed, nullptr);
    manager.enablePlugin(*failed);
    EXPECT_FALSE(failed->isEnabled());
    EXPECT_FALSE(commands.getCommand("partial"));
    EXPECT_FALSE(manager.isEventRegistered<PlayerJoinEvent>());
    EXPECT_TRUE(scheduler.getPendingTasks().empty());
    manager.clearPlugins();
}
