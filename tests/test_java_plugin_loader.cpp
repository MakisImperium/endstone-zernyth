#include <charconv>
#include <chrono>
#include <cstdlib>
#include <thread>

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "endstone/core/logger_factory.h"
#include "endstone/core/plugin/java_plugin_loader.h"
#include "endstone/core/plugin/plugin_manager.h"
#include "endstone/core/plugin/service_manager.h"
#include "endstone/core/scheduler/scheduler.h"
#include "java_command_map.h"
#include "mocks.h"

namespace {
void expectVersionRejection(const char *key)
{
    const auto *classpath = std::getenv(key);
    const auto *jar = std::getenv("ZERNYTH_TEST_PLUGIN_JAR");
    if (!classpath || !jar) {
        GTEST_SKIP() << "Provide synthetic version-mismatch classpath";
    }
    const std::string classpath_copy = classpath, jar_copy = jar;
#ifdef _WIN32
    ASSERT_EQ(_putenv_s("ZERNYTH_JAVA_CLASSPATH", classpath_copy.c_str()), 0);
#else
    ASSERT_EQ(setenv("ZERNYTH_JAVA_CLASSPATH", classpath_copy.c_str(), 1), 0);
#endif
    testing::NiceMock<MockServer> server;
    ON_CALL(server, isPrimaryThread()).WillByDefault(testing::Return(true));
    ON_CALL(server, getLogger())
        .WillByDefault(testing::ReturnRef(endstone::core::LoggerFactory::getLogger("VersionTest")));
    endstone::core::JavaPluginLoader loader(server, nullptr, {});
    EXPECT_EQ(loader.loadPlugin(jar_copy), nullptr);
    EXPECT_EQ(loader.loadPlugin(jar_copy), nullptr);  // Failure is sticky; never restart the JVM.
}
}  // namespace
TEST(JavaVersionTest, RejectApiRuntimeMismatch)
{
    expectVersionRejection("ZERNYTH_API_MISMATCH_CLASSPATH");
}
TEST(JavaVersionTest, RejectNativeRuntimeMismatch)
{
    expectVersionRejection("ZERNYTH_NATIVE_MISMATCH_CLASSPATH");
}

// Opt-in production bridge test. No BDS is started; only Server is mocked.
TEST(JavaPluginLoaderTest, ProductionBridgeThroughExistingManager)
{
    const auto *jar = std::getenv("ZERNYTH_TEST_PLUGIN_JAR");
    if (!jar || !std::getenv("ZERNYTH_JVM_LIBRARY") || !std::getenv("ZERNYTH_JAVA_CLASSPATH")) {
        GTEST_SKIP() << "Set ZERNYTH_TEST_PLUGIN_JAR, ZERNYTH_JVM_LIBRARY and ZERNYTH_JAVA_CLASSPATH";
    }
    testing::NiceMock<MockServer> server;
    endstone::core::EndstoneScheduler scheduler(server);
    endstone::core::EndstoneServiceManager services;
    endstone::GameplayCommands commands;
    endstone::core::EndstonePluginManager manager(server);
    bool primary = true;
    ON_CALL(server, isPrimaryThread()).WillByDefault([&] { return primary; });
    ON_CALL(server, getLogger())
        .WillByDefault(testing::ReturnRef(endstone::core::LoggerFactory::getLogger("JavaTest")));
    ON_CALL(server, getPluginManager()).WillByDefault(testing::ReturnRef(manager));
    ON_CALL(server, getScheduler()).WillByDefault(testing::ReturnRef(scheduler));
    ON_CALL(server, getServiceManager()).WillByDefault(testing::ReturnRef(services));
    manager.registerLoader(std::make_unique<endstone::core::JavaPluginLoader>(
        server, &commands,
        [&](const std::shared_ptr<endstone::Command> &entry) { commands.entries.erase(entry->getName()); }));

    primary = false;
    EXPECT_EQ(manager.loadPlugin(jar), nullptr);  // Reject before JVM/plugin access.
    primary = true;
    EXPECT_EQ(manager.loadPlugin("missing-zernyth-test.jar"), nullptr);  // Java exception boundary.
    auto *plugin = manager.loadPlugin(jar);
    ASSERT_NE(plugin, nullptr);
    EXPECT_EQ(manager.getPlugin("hello_zernyth"), plugin);
    EXPECT_FALSE(plugin->isEnabled());
    EXPECT_EQ(manager.loadPlugin(jar), nullptr);  // Existing manager rejects duplicate name.
    manager.enablePlugin(*plugin);                // HelloZernyth calls the real registered native logger.
    ASSERT_TRUE(plugin->isEnabled());
    auto task = scheduler.runTaskLater(*plugin, [] {}, 100);
    ASSERT_NE(task, nullptr);
    EXPECT_FALSE(task->isCancelled());
    manager.disablePlugin(*plugin);
    EXPECT_FALSE(plugin->isEnabled());
    EXPECT_TRUE(task->isCancelled());  // Existing manager's owner cleanup.
    manager.enablePlugin(*plugin);     // Same VM, fresh loader/instance and owner generation.
    EXPECT_TRUE(plugin->isEnabled());
    if (const auto *cycles = std::getenv("ZERNYTH_LIFECYCLE_CYCLES")) {
        int count = 0;
        const auto end = cycles + std::char_traits<char>::length(cycles);
        const auto parsed = std::from_chars(cycles, end, count);
        ASSERT_EQ(parsed.ec, std::errc{});
        ASSERT_EQ(parsed.ptr, end);
        ASSERT_GE(count, 1);
        ASSERT_LE(count, 50);
        int pause = 0;
        if (const auto *text = std::getenv("ZERNYTH_LIFECYCLE_PAUSE_MS")) {
            const auto last = text + std::char_traits<char>::length(text);
            const auto result = std::from_chars(text, last, pause);
            ASSERT_EQ(result.ec, std::errc{});
            ASSERT_EQ(result.ptr, last);
            ASSERT_GE(pause, 0);
            ASSERT_LE(pause, 1000);
        }
        for (int i = 0; i < count; ++i) {
            manager.disablePlugin(*plugin);
            EXPECT_FALSE(plugin->isEnabled());
            manager.enablePlugin(*plugin);
            ASSERT_TRUE(plugin->isEnabled());
            if (pause) {
                std::this_thread::sleep_for(std::chrono::milliseconds(pause));
            }
        }
    }
    manager.clearPlugins();  // Disable and release Java globalref/ClassLoader.
    EXPECT_EQ(manager.getPlugin("hello_zernyth"), nullptr);
}
