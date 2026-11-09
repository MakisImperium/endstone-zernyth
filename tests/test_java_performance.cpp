#include <chrono>
#include <cstdlib>

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

namespace {
jint JNICALL ping(JNIEnv *, jclass, jint value)
{
    return value + 1;
}
}  // namespace
TEST(JavaPerformanceTest, ReproducibleProductionBridgeHarness)
{
    using namespace endstone;
    using namespace endstone::core;
    auto jar = std::getenv("ZERNYTH_BENCHMARK_JAR");
    if (!jar || !std::getenv("ZERNYTH_PERF_OUTPUT")) {
        GTEST_SKIP() << "Set benchmark JAR/output and JNI paths";
    }
    testing::NiceMock<MockServer> server;
    testing::NiceMock<GameplayMockPlayer> player;
    testing::NiceMock<GameplayMockConsole> console;
    EndstoneScheduler scheduler(server);
    EndstoneServiceManager services;
    GameplayCommands commands;
    EndstonePluginManager manager(server);
    ON_CALL(server, isPrimaryThread()).WillByDefault(testing::Return(true));
    ON_CALL(server, getLogger()).WillByDefault(testing::ReturnRef(LoggerFactory::getLogger("Benchmark")));
    ON_CALL(server, getPluginManager()).WillByDefault(testing::ReturnRef(manager));
    ON_CALL(server, getScheduler()).WillByDefault(testing::ReturnRef(scheduler));
    ON_CALL(server, getServiceManager()).WillByDefault(testing::ReturnRef(services));
    ON_CALL(server, getMinecraftVersion()).WillByDefault(testing::Return("26.60"));
    ON_CALL(server, getProtocolVersion()).WillByDefault(testing::Return(2225));
    ON_CALL(server, getOnlinePlayers()).WillByDefault(testing::Return(std::vector<endstone::Player *>{&player}));
    ON_CALL(server, getPlayer(testing::An<UUID>())).WillByDefault(testing::Return(&player));
    ON_CALL(player, getName()).WillByDefault(testing::Return("Benchmark"));
    ON_CALL(player, getUniqueId()).WillByDefault(testing::Return(UUID{}));
    ON_CALL(console, asConsole()).WillByDefault(testing::Return(&console));
    manager.registerLoader(std::make_unique<JavaPluginLoader>(
        server, &commands, [&](const std::shared_ptr<Command> &c) { commands.entries.erase(c->getName()); }));
    auto *plugin = manager.loadPlugin(jar);
    ASSERT_NE(plugin, nullptr);
    manager.enablePlugin(*plugin);
    ASSERT_TRUE(plugin->isEnabled());
    java::details::Env env;
    auto e = env.e;
    auto cls = e->FindClass("dev/zernyth/runtime/BenchmarkHarness");
    java::details::callbackCheck(e);
    JNINativeMethod method{const_cast<char *>("ping"), const_cast<char *>("(I)I"), reinterpret_cast<void *>(ping)};
    ASSERT_EQ(e->RegisterNatives(cls, &method, 1), JNI_OK);
    auto basic = e->GetStaticMethodID(cls, "basic", "(Ljava/lang/String;)V");
    e->CallStaticVoidMethod(cls, basic, e->NewStringUTF(jar));
    java::details::callbackCheck(e);
    for (const auto &name : {"players", "strings", "services", "registration", "schedule"}) {
        ASSERT_TRUE(commands.dispatch(console, std::string("perfbench ") + name));
    }
    auto record = e->GetStaticMethodID(cls, "record", "(Ljava/lang/String;[JI)V");
    auto measure = [&](const char *name, int count, auto action) {
        for (int w = 0; w < 3; ++w) {
            for (int i = 0; i < count; ++i) {
                action();
            }
        }
        jlong samples[15];
        for (auto &sample : samples) {
            auto start = std::chrono::steady_clock::now();
            for (int i = 0; i < count; ++i) {
                action();
            }
            sample =
                std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - start).count();
        }
        auto values = e->NewLongArray(15);
        e->SetLongArrayRegion(values, 0, 15, samples);
        e->CallStaticVoidMethod(cls, record, e->NewStringUTF(name), values, count);
        java::details::callbackCheck(e);
    };
    auto *nativeListener =
        manager.registerManagedEvent("BenchmarkNativeEvent", [](Event &) {}, EventPriority::Normal, *plugin, false);
    class EventProbe final : public Event {
    public:
        std::string getEventName() const override { return "BenchmarkNativeEvent"; }
    } nativeEvent;
    measure("native-event-dispatch", 2000, [&] { manager.callEvent(nativeEvent); });
    manager.unregisterManagedEvent(*nativeListener);
    PlayerJoinEvent join(player, std::nullopt);
    measure("production-java-event-dispatch", 2000, [&] { manager.callEvent(join); });
    PacketSendEvent packet(&player, 9, "", {}, 0);
    measure("production-packet-listener", 2000, [&] { manager.callEvent(packet); });
    int tick = 0;
    std::uint64_t executed = 0;
    measure("native-scheduler-enqueue-heartbeat", 200, [&] {
        scheduler.runTask(*plugin, [&] { ++executed; });
        scheduler.mainThreadHeartbeat(++tick);
    });
    measure("plugin-disable-enable-cleanup", 2, [&] {
        manager.disablePlugin(*plugin);
        manager.enablePlugin(*plugin);
    });
    manager.clearPlugins();
    auto finish = e->GetStaticMethodID(cls, "finish", "()V");
    e->CallStaticVoidMethod(cls, finish);
    java::details::callbackCheck(e);
}
