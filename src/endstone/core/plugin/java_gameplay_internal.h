#pragma once
#include <atomic>
#include <chrono>
#include <cmath>
#include <limits>
#include <mutex>
#include <thread>
#include <unordered_map>

#include "endstone/command/command.h"
#include "endstone/core/plugin/java_gameplay.h"
#include "endstone/core/plugin/java_handle_ids.h"
#include "endstone/core/plugin/plugin_manager.h"
#include "endstone/event/block/block_break_event.h"
#include "endstone/event/block/block_place_event.h"
#include "endstone/event/player/player_chat_event.h"
#include "endstone/event/player/player_interact_event.h"
#include "endstone/event/player/player_join_event.h"
#include "endstone/event/player/player_move_event.h"
#include "endstone/event/player/player_quit_event.h"
#include "endstone/level/dimension.h"
#include "endstone/level/level.h"
#include "endstone/player.h"
#include "endstone/scheduler/scheduler.h"

namespace endstone::core::java::details {
inline JavaVM *jvm = nullptr;
// Preserve a pending Java exception and stop normal JNI work. The boundary below keeps
// that exception instead of issuing further class/method lookups while it is pending.
inline void checked(JNIEnv *e)
{
    if (e->ExceptionCheck()) {
        throw std::runtime_error("Pending Java exception");
    }
}
// Scope-owned JNI local frame and, only when necessary, thread attachment. JNIEnv is
// thread-local: neither this scope nor its local references may escape to another thread.
// Destruction pops the frame and detaches only a thread attached by this scope.
struct Env {
    JNIEnv *e = nullptr;
    bool attached = false;
    Env()
    {
        auto status = jvm->GetEnv(reinterpret_cast<void **>(&e), JNI_VERSION_24);
        if (status == JNI_EDETACHED) {
            if (jvm->AttachCurrentThreadAsDaemon(reinterpret_cast<void **>(&e), nullptr) != JNI_OK) {
                throw std::runtime_error("JVM thread attachment failed");
            }
            attached = true;
        }
        else if (status != JNI_OK) {
            throw std::runtime_error("Invalid JNI environment");
        }
        if (e->PushLocalFrame(64) != JNI_OK) {
            e->ExceptionClear();
            if (attached) {
                jvm->DetachCurrentThread();
            }
            throw std::runtime_error("JNI frame allocation failed");
        }
    }
    ~Env()
    {
        e->PopLocalFrame(nullptr);
        if (attached) {
            jvm->DetachCurrentThread();
        }
    }
};
// Shared ownership pins a callback across reentrant unregister/disable. The last owner
// releases exactly one global reference; the process-owned JVM outlives these objects.
// This is callback ownership, never ownership of the referenced native BDS objects.
struct Ref {
    jobject value;
    Ref(JNIEnv *e, jobject object) : value(e->NewGlobalRef(object))
    {
        checked(e);
        if (!value) {
            throw std::runtime_error("Null Java callback");
        }
    }
    ~Ref()
    {
        // Releasing a global ref needs no local frame/allocation, even on an exception path.
        JNIEnv *env = nullptr;
        bool attached = false;
        auto status = jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_24);
        if (status == JNI_EDETACHED) {
            status = jvm->AttachCurrentThreadAsDaemon(reinterpret_cast<void **>(&env), nullptr);
            attached = status == JNI_OK;
        }
        if (status == JNI_OK) {
            env->DeleteGlobalRef(value);
        }
        if (attached) {
            jvm->DetachCurrentThread();
        }
    }
};
using Global = std::shared_ptr<Ref>;
inline std::string text(JNIEnv *e, jstring value)
{
    if (!value) {
        throw std::invalid_argument("Null text");
    }
    // Convert Java UTF-16 through standard UTF-8, preserving supplementary characters and NUL.
    const auto *chars = e->GetStringChars(value, nullptr);
    checked(e);
    if (!chars) {
        throw std::runtime_error("Java string access failed");
    }
    try {
        std::string out;
        const auto length = e->GetStringLength(value);
        for (jsize i = 0; i < length; ++i) {
            std::uint32_t cp = chars[i];
            if (cp >= 0xd800 && cp <= 0xdbff && i + 1 < length && chars[i + 1] >= 0xdc00 && chars[i + 1] <= 0xdfff) {
                cp = 0x10000 + ((cp - 0xd800) << 10) + (chars[++i] - 0xdc00);
            }
            else if (cp >= 0xd800 && cp <= 0xdfff) {
                cp = 0xfffd;
            }
            if (cp < 0x80) {
                out.push_back(static_cast<char>(cp));
            }
            else if (cp < 0x800) {
                out.push_back(static_cast<char>(0xc0 | (cp >> 6)));
                out.push_back(static_cast<char>(0x80 | (cp & 63)));
            }
            else if (cp < 0x10000) {
                out.push_back(static_cast<char>(0xe0 | (cp >> 12)));
                out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 63)));
                out.push_back(static_cast<char>(0x80 | (cp & 63)));
            }
            else {
                out.push_back(static_cast<char>(0xf0 | (cp >> 18)));
                out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 63)));
                out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 63)));
                out.push_back(static_cast<char>(0x80 | (cp & 63)));
            }
        }
        e->ReleaseStringChars(value, chars);
        return out;
    }
    catch (...) {
        e->ReleaseStringChars(value, chars);
        throw;
    }
}
// Bootstrap JDK types only: these references share the one JVM's process lifetime.
// Never cache a plugin class or plugin ClassLoader here.
inline jclass stringClass = nullptr;
inline jmethodID stringConstructor = nullptr;
inline jstring utf8Name = nullptr;
inline void initializeStringCodec(JNIEnv *e)
{
    auto local = e->FindClass("java/lang/String");
    checked(e);
    auto constructor = e->GetMethodID(local, "<init>", "([BLjava/lang/String;)V");
    checked(e);
    auto charset = e->NewStringUTF("UTF-8");
    checked(e);
    auto cls = static_cast<jclass>(e->NewGlobalRef(local));
    checked(e);
    try {
        auto name = static_cast<jstring>(e->NewGlobalRef(charset));
        checked(e);
        if (!cls || !name) {
            throw std::runtime_error("String codec retention failed");
        }
        stringClass = cls;
        stringConstructor = constructor;
        utf8Name = name;
    }
    catch (...) {
        if (cls) {
            e->DeleteGlobalRef(cls);
        }
        throw;
    }
    e->DeleteLocalRef(charset);
    e->DeleteLocalRef(local);
}
inline jstring string(JNIEnv *e, const std::string &value)
{
    // Use the JDK's UTF-8 decoder rather than NewStringUTF's modified UTF-8 format.
    auto bytes = e->NewByteArray(static_cast<jsize>(value.size()));
    checked(e);
    e->SetByteArrayRegion(bytes, 0, static_cast<jsize>(value.size()), reinterpret_cast<const jbyte *>(value.data()));
    checked(e);
    auto result = static_cast<jstring>(e->NewObject(stringClass, stringConstructor, bytes, utf8Name));
    checked(e);
    e->DeleteLocalRef(bytes);
    return result;
}
inline jobjectArray strings(JNIEnv *e, const std::vector<std::string> &items)
{
    auto out = e->NewObjectArray(static_cast<jsize>(items.size()), stringClass, nullptr);
    checked(e);
    for (jsize i = 0; i < static_cast<jsize>(items.size()); ++i) {
        auto s = string(e, items[i]);
        e->SetObjectArrayElement(out, i, s);
        checked(e);
        e->DeleteLocalRef(s);
    }
    return out;
}
struct BridgeCounters {
    std::atomic<bool> enabled{false};
    std::atomic<std::uint64_t> calls{0}, nanos{0}, maximum{0}, errors{0};
};
inline std::atomic<int> activeBridgeProfiles{0};
inline thread_local std::shared_ptr<BridgeCounters> currentBridge;
struct BridgeMeasurement {
    std::shared_ptr<BridgeCounters> previous = std::move(currentBridge);
    std::chrono::steady_clock::time_point start =
        activeBridgeProfiles.load() > 0 ? std::chrono::steady_clock::now() : std::chrono::steady_clock::time_point{};
    bool failed = false;
    ~BridgeMeasurement()
    {
        auto counters = std::move(currentBridge);
        currentBridge = std::move(previous);
        if (!counters || !counters->enabled) {
            return;
        }
        ++counters->calls;
        if (failed) {
            ++counters->errors;
        }
        if (start == std::chrono::steady_clock::time_point{}) {
            return;
        }
        auto elapsed = static_cast<std::uint64_t>(
            std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - start).count());
        counters->nanos += elapsed;
        auto old = counters->maximum.load();
        while (old < elapsed && !counters->maximum.compare_exchange_weak(old, elapsed)) {
        }
    }
};
template <class F>
auto boundary(JNIEnv *e, F f) noexcept -> decltype(f())
{
    BridgeMeasurement measurement;
    try {
        return f();
    }
    catch (const std::exception &error) {
        measurement.failed = true;
        if (!e->ExceptionCheck()) {
            auto type = e->FindClass("java/lang/IllegalStateException");
            if (type) {
                e->ThrowNew(type, error.what());
            }
        }
    }
    catch (...) {
        measurement.failed = true;
        if (!e->ExceptionCheck()) {
            auto type = e->FindClass("java/lang/IllegalStateException");
            if (type) {
                e->ThrowNew(type, "Unknown native gameplay error");
            }
        }
    }
    if constexpr (!std::is_void_v<decltype(f())>) {
        return {};
    }
}
inline std::atomic<jlong> next{1};
inline jlong id()
{
    return allocateId(next);
}
struct Subscription {
    Global callback;
    EventHandler *handler = nullptr;
    std::atomic<bool> active{true};
};
struct PendingEvent {
    Event *event;
    bool monitor;
};
struct PendingSender {
    CommandSender *borrowed;
    std::optional<UUID> player;
};
inline std::atomic<std::size_t> activeJavaJobs{0};
struct Job {
    std::mutex mutex;
    Global callback;
    std::weak_ptr<Task> task;
    std::atomic<bool> active{true};
    std::atomic<bool> cancelled{false};
    std::shared_ptr<std::atomic<std::size_t>> budget;
    ~Job() { release(); }
    void release(bool cancel = false)
    {
        if (cancel) {
            cancelled = true;
        }
        if (active.exchange(false) && budget) {
            --*budget;
            --activeJavaJobs;
        }
        std::lock_guard lock(mutex);
        callback.reset();
    }
};
struct AdvancedState;
struct NetworkState;
struct EcosystemState;
struct Context {
    struct PlayerRef {
        UUID uuid;
        std::uint64_t runtime;
    };
    std::shared_ptr<BridgeCounters> bridge = std::make_shared<BridgeCounters>();
    std::shared_ptr<std::atomic<std::size_t>> liveJobs = std::make_shared<std::atomic<std::size_t>>(0);
    std::shared_ptr<EcosystemState> ecosystem;
    std::shared_ptr<NetworkState> network;
    std::shared_ptr<AdvancedState> advanced;
    Plugin *plugin;
    Server *server;
    Logger *logger;
    CommandMap *commands;
    RemoveCommand remove;
    std::atomic<std::thread::id> main;
    std::atomic<bool> active{true};
    std::atomic<bool> enabledPhase{false};
    std::unordered_map<jlong, PlayerRef> players;
    std::unordered_map<jlong, PendingEvent> events;
    std::unordered_map<jlong, PendingSender> senders;
    std::unordered_map<jlong, std::shared_ptr<Subscription>> subscriptions;
    std::unordered_map<jlong, std::shared_ptr<Command>> registeredCommands;
    std::unordered_map<jlong, std::shared_ptr<Job>> jobs;
};
inline std::mutex registryMutex;
inline std::unordered_map<jlong, std::shared_ptr<Context>> contexts;
inline std::shared_ptr<Context> context(jlong owner, bool enabled = false)
{
    std::shared_ptr<Context> c;
    {
        std::lock_guard lock(registryMutex);
        auto it = contexts.find(owner);
        if (it == contexts.end()) {
            throw std::runtime_error("Expired plugin context");
        }
        c = it->second;
    }
    if (c->bridge->enabled) {
        currentBridge = c->bridge;
    }
    // Reject worker access before dereferencing Server/Plugin (which may be tearing down).
    if (c->main.load() != std::this_thread::get_id()) {
        throw std::runtime_error("Gameplay API requires server thread");
    }
    if (!c->active || !c->server->isPrimaryThread()) {
        throw std::runtime_error("Inactive context or wrong server thread");
    }
    if (enabled && (!c->enabledPhase || !c->plugin->isEnabled())) {
        throw std::runtime_error("Plugin must be enabled");
    }
    return c;
}
inline jlong playerId(Context &c, Player &player)
{
    const auto uuid = player.getUniqueId();
    const auto runtime = player.getRuntimeId();
    for (const auto &[token, value] : c.players) {
        if (value.uuid == uuid && value.runtime == runtime) {
            return token;
        }
    }
    auto token = id();
    c.players.emplace(token, Context::PlayerRef{uuid, runtime});
    return token;
}
inline Player *resolve(Context &c, jlong token)
{
    auto it = c.players.find(token);
    if (it == c.players.end()) {
        throw std::runtime_error("Invalid player handle");
    }
    auto *current = c.server->getPlayer(it->second.uuid);
    if (current && current->getRuntimeId() != it->second.runtime) {
        throw std::runtime_error("Stale player session handle");
    }
    return current;
}
inline Player &player(Context &c, jlong token)
{
    auto *p = resolve(c, token);
    if (!p) {
        throw std::runtime_error("Player is offline");
    }
    return *p;
}
inline CommandSender &sender(Context &c, jlong token)
{
    auto it = c.senders.find(token);
    if (it == c.senders.end()) {
        throw std::runtime_error("Expired command sender");
    }
    if (it->second.player) {
        auto *current = c.server->getPlayer(*it->second.player);
        if (!current) {
            throw std::runtime_error("Command player is offline");
        }
        return *current;
    }
    return *it->second.borrowed;
}
inline PendingEvent &event(Context &c, jlong token)
{
    auto it = c.events.find(token);
    if (it == c.events.end()) {
        throw std::runtime_error("Expired event view");
    }
    return it->second;
}
inline std::vector<std::string> location(const Location &l)
{
    return {l.getDimension().getName(), std::to_string(l.getX()),     std::to_string(l.getY()),
            std::to_string(l.getZ()),   std::to_string(l.getPitch()), std::to_string(l.getYaw())};
}
inline void callbackCheck(JNIEnv *e)
{
    if (e->ExceptionCheck()) {
        e->ExceptionDescribe();
        e->ExceptionClear();
        throw std::runtime_error("Java gameplay callback failed (see JVM stderr)");
    }
}
inline Player &eventPlayer(Event &e)
{
    if (auto *p = dynamic_cast<PlayerEvent *>(&e)) {
        return p->getPlayer();
    }
    if (auto *p = dynamic_cast<BlockBreakEvent *>(&e)) {
        return p->getPlayer();
    }
    if (auto *p = dynamic_cast<BlockPlaceEvent *>(&e)) {
        return p->getPlayer();
    }
    throw std::runtime_error("Unsupported player event");
}
inline EndstonePluginManager &manager(Context &c)
{
    auto *m = dynamic_cast<EndstonePluginManager *>(&c.server->getPluginManager());
    if (!m) {
        throw std::runtime_error("Endstone plugin manager required");
    }
    return *m;
}
void closeAdvanced(Context &context);
void closeNetwork(Context &context);
void closeEcosystem(Context &context);
jlong networkListenerCount(Context &context);
}  // namespace endstone::core::java::details
