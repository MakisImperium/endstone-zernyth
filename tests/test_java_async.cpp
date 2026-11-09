#include <algorithm>
#include <array>
#include <chrono>
#include <cstdlib>
#include <thread>

#include "endstone/block/block_state.h"
#include "endstone/core/logger_factory.h"
#include "endstone/core/plugin/java_gameplay_internal.h"
#include "endstone/core/plugin/java_plugin_loader.h"
#include "endstone/core/plugin/service_manager.h"
#include "endstone/core/scheduler/scheduler.h"
#include "endstone/event/player/player_join_event.h"
#include "endstone/event/server/packet_send_event.h"
#include "java_command_map.h"
#include "java_gameplay_mocks.h"
#include "mocks.h"
TEST(JavaHandleTest, BoundedConcurrentIdsAreUnique)
{
    using endstone::core::java::details::allocateId;
    std::atomic<jlong> counter{1};
    std::array<std::array<jlong, 1000>, 4> values{};
    std::array<std::thread, 4> workers;
    for (int w = 0; w < 4; ++w) {
        workers[w] = std::thread([&, w] {
            for (auto &value : values[w]) {
                value = allocateId(counter);
            }
        });
    }
    for (auto &worker : workers) {
        worker.join();
    }
    std::vector<jlong> all;
    for (auto &group : values) {
        all.insert(all.end(), group.begin(), group.end());
    }
    std::sort(all.begin(), all.end());
    EXPECT_EQ(all.size(), 4000);
    EXPECT_EQ(std::adjacent_find(all.begin(), all.end()), all.end());
    EXPECT_EQ(counter.load(), 4001);
}
TEST(JavaHandleTest, ExhaustionNeverWrapsOrRecycles)
{
    using namespace endstone::core::java::details;
    std::atomic<jlong> counter{(std::numeric_limits<jlong>::max)() - 1};
    EXPECT_EQ(allocateId(counter), (std::numeric_limits<jlong>::max)() - 1);
    EXPECT_THROW(allocateId(counter), std::runtime_error);
    EXPECT_THROW(allocateId(counter), std::runtime_error);
    EXPECT_EQ(counter.load(), (std::numeric_limits<jlong>::max)());
}
TEST(JavaHandleTest, SameUuidNewRuntimeRejectsOldPlayerHandle)
{
    using namespace endstone;
    using namespace endstone::core::java::details;
    testing::NiceMock<MockServer> server;
    testing::NiceMock<GameplayMockPlayer> player;
    std::uint64_t runtime = 1;
    ON_CALL(player, getUniqueId()).WillByDefault(testing::Return(UUID{}));
    ON_CALL(player, getRuntimeId()).WillByDefault([&] { return runtime; });
    ON_CALL(server, getPlayer(testing::An<UUID>())).WillByDefault(testing::Return(&player));
    Context context;
    context.server = &server;
    auto old = playerId(context, player);
    EXPECT_EQ(resolve(context, old), &player);
    runtime = 2;
    EXPECT_THROW(resolve(context, old), std::runtime_error);
    auto fresh = playerId(context, player);
    EXPECT_NE(old, fresh);
    EXPECT_EQ(resolve(context, fresh), &player);
}
TEST(JavaAsyncTest, ProductionSchedulerVirtualThreadAndAffinity)
{
    using namespace endstone;
    using namespace endstone::core;
    auto jar = std::getenv("ZERNYTH_ASYNC_JAR");
    if (!jar) {
        GTEST_SKIP() << "Set ZERNYTH_ASYNC_JAR and JNI runtime paths";
    }
    testing::NiceMock<MockServer> server;
    testing::NiceMock<GameplayMockPlayer> player;
    testing::NiceMock<GameplayMockConsole> console;
    EndstoneScheduler scheduler(server);
    EndstoneServiceManager services;
    GameplayCommands commands;
    EndstonePluginManager manager(server);
    ON_CALL(server, isPrimaryThread()).WillByDefault(testing::Return(true));
    ON_CALL(server, getLogger()).WillByDefault(testing::ReturnRef(LoggerFactory::getLogger("AsyncTest")));
    ON_CALL(server, getPluginManager()).WillByDefault(testing::ReturnRef(manager));
    ON_CALL(server, getScheduler()).WillByDefault(testing::ReturnRef(scheduler));
    ON_CALL(server, getServiceManager()).WillByDefault(testing::ReturnRef(services));
    ON_CALL(server, getMinecraftVersion()).WillByDefault(testing::Return("26.60"));
    ON_CALL(server, getProtocolVersion()).WillByDefault(testing::Return(2225));
    ON_CALL(server, getOnlinePlayers()).WillByDefault(testing::Return(std::vector<endstone::Player *>{&player}));
    ON_CALL(server, getPlayer(testing::An<UUID>())).WillByDefault(testing::Return(&player));
    ON_CALL(player, getName()).WillByDefault(testing::Return("AsyncTest"));
    ON_CALL(player, getUniqueId()).WillByDefault(testing::Return(UUID{}));
    ON_CALL(console, asConsole()).WillByDefault(testing::Return(&console));
    manager.registerLoader(std::make_unique<JavaPluginLoader>(
        server, &commands, [&](const std::shared_ptr<Command> &c) { commands.entries.erase(c->getName()); }));
    auto *plugin = manager.loadPlugin(jar);
    ASSERT_NE(plugin, nullptr);
    manager.enablePlugin(*plugin);
    ASSERT_TRUE(plugin->isEnabled());
    bool verified = false;
    EXPECT_CALL(player, sendMessage(testing::_)).WillOnce([](const Message &message) {
        ASSERT_TRUE(std::holds_alternative<Translatable>(message));
        EXPECT_EQ(std::get<Translatable>(message).getParameters(), std::vector<std::string>{"player"});
    });
    EXPECT_CALL(console, sendMessage(testing::_)).WillOnce([](const Message &message) {
        ASSERT_TRUE(std::holds_alternative<Translatable>(message));
        EXPECT_EQ(std::get<Translatable>(message).getText(), "zernyth.test");
        EXPECT_EQ(std::get<Translatable>(message).getParameters(), std::vector<std::string>{"native"});
    });
    for (int tick = 1; tick <= 400 && !verified; ++tick) {
        scheduler.mainThreadHeartbeat(tick);
        verified = commands.dispatch(console, "asyncverify");
        if (!verified) {
            std::this_thread::sleep_for(std::chrono::milliseconds(5));
        }
    }
    EXPECT_TRUE(verified);
    EXPECT_CALL(console, sendMessage(testing::_)).WillOnce([&](const Message &message) {
        ASSERT_TRUE(std::holds_alternative<std::string>(message));
        EXPECT_EQ(std::get<std::string>(message), "reentrant-disable");
        manager.disablePlugin(*plugin);  // Re-enter lifecycle while a Java/JNI command is on the stack.
    });
    EXPECT_TRUE(commands.dispatch(console, "reentrantdisable"));
    EXPECT_FALSE(plugin->isEnabled());
    EXPECT_TRUE(scheduler.getPendingTasks().empty());
    manager.clearPlugins();
}
