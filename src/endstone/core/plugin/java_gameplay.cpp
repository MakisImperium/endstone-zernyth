#include "endstone/core/plugin/java_gameplay_internal.h"

namespace endstone::core::java {
using namespace details;
namespace {
jlongArray JNICALL players(JNIEnv *e, jclass, jlong owner)
{
    return boundary(e, [&] {
        auto c = context(owner);
        std::vector<jlong> result;
        for (auto *p : c->server->getOnlinePlayers()) {
            if (p) {
                result.push_back(playerId(*c, *p));
            }
        }
        auto out = e->NewLongArray(static_cast<jsize>(result.size()));
        checked(e);
        e->SetLongArrayRegion(out, 0, static_cast<jsize>(result.size()), result.data());
        checked(e);
        return out;
    });
}
jobjectArray JNICALL playerData(JNIEnv *e, jclass, jlong owner, jlong token, jint kind)
{
    return boundary(e, [&] {
        auto c = context(owner);
        auto &p = player(*c, token);
        if (kind == 0) {
            return strings(e, {p.getName(), p.getUniqueId().str()});
        }
        if (kind == 1) {
            return strings(e, location(p.getLocation()));
        }
        throw std::runtime_error("Invalid player query");
    });
}
jint JNICALL playerNumber(JNIEnv *e, jclass, jlong owner, jlong token, jint kind)
{
    return boundary(e, [&]() -> jint {
        auto c = context(owner);
        if (kind == 0) {
            return resolve(*c, token) != nullptr;
        }
        auto &p = player(*c, token);
        if (kind == 1) {
            return p.getHealth();
        }
        if (kind == 2) {
            return static_cast<int>(p.getGameMode());
        }
        throw std::runtime_error("Invalid player query");
    });
}
jboolean JNICALL playerAction(JNIEnv *e, jclass, jlong owner, jlong token, jint kind, jstring message,
                              jdoubleArray data)
{
    return boundary(e, [&]() -> jboolean {
        auto c = context(owner);
        auto &p = player(*c, token);
        if (kind == 0) {
            p.sendMessage(text(e, message));
            return true;
        }
        if (kind == 1) {
            p.kick(text(e, message));
            return true;
        }
        if (!data) {
            throw std::runtime_error("Missing action arguments");
        }
        const auto size = e->GetArrayLength(data);
        checked(e);
        if ((kind == 2 && size != 5) || (kind == 3 && size != 1)) {
            throw std::runtime_error("Invalid action arguments");
        }
        double values[5]{};
        if (size > 5) {
            throw std::runtime_error("Too many action arguments");
        }
        e->GetDoubleArrayRegion(data, 0, size, values);
        checked(e);
        for (int i = 0; i < size; ++i) {
            if (!std::isfinite(values[i]) || std::abs(values[i]) > std::numeric_limits<float>::max()) {
                throw std::runtime_error("Invalid coordinates");
            }
        }
        if (kind == 3) {
            if (values[0] < 0 || values[0] > 3 || std::floor(values[0]) != values[0]) {
                throw std::runtime_error("Invalid game mode");
            }
            p.setGameMode(static_cast<GameMode>(static_cast<int>(values[0])));
            return true;
        }
        if (kind == 2) {
            auto *level = c->server->getLevel();
            if (!level) {
                throw std::runtime_error("No active level");
            }
            auto *dim = level->getDimension(text(e, message));
            if (!dim) {
                throw std::runtime_error("Unknown dimension");
            }
            return p.teleport(Location(*dim, values[0], values[1], values[2], static_cast<float>(values[3]),
                                       static_cast<float>(values[4])));
        }
        throw std::runtime_error("Invalid player action");
    });
}
jboolean JNICALL permission(JNIEnv *e, jclass, jlong owner, jlong token, jstring value, jboolean isSender)
{
    return boundary(e, [&]() -> jboolean {
        auto c = context(owner);
        auto name = text(e, value);
        return isSender ? sender(*c, token).hasPermission(name) : player(*c, token).hasPermission(name);
    });
}
jlong JNICALL subscribe(JNIEnv *e, jclass, jlong owner, jstring name, jint priority, jboolean ignore, jobject callback)
{
    return boundary(e, [&] {
        auto c = context(owner, true);
        auto eventName = text(e, name);
        static const std::vector<std::string> supported = {"PlayerJoinEvent", "PlayerQuitEvent",     "PlayerChatEvent",
                                                           "PlayerMoveEvent", "PlayerInteractEvent", "BlockBreakEvent",
                                                           "BlockPlaceEvent"};
        if (std::find(supported.begin(), supported.end(), eventName) == supported.end() || priority < 0 ||
            priority > 5) {
            throw std::runtime_error("Invalid event registration");
        }
        auto sub = std::make_shared<Subscription>();
        sub->callback = std::make_shared<Ref>(e, callback);
        auto cls = e->GetObjectClass(callback);
        checked(e);
        auto method = e->GetMethodID(cls, "accept", "(JJ)V");
        checked(e);
        std::weak_ptr<Context> weak = c;
        sub->handler = manager(*c).registerManagedEvent(
            eventName,
            [weak, sub, method, priority](Event &native) {
                auto c = weak.lock();
                if (!c || !c->active || !sub->active) {
                    return;
                }
                if (c->main.load() != std::this_thread::get_id()) {
                    throw std::runtime_error("Java gameplay events must be synchronous");
                }
                auto ref = sub->callback;
                if (!ref) {
                    return;
                }
                auto token = id();
                c->events.emplace(token, PendingEvent{&native, priority == 5});
                try {
                    Env env;
                    env.e->CallVoidMethod(ref->value, method, token, playerId(*c, eventPlayer(native)));
                    callbackCheck(env.e);
                    c->events.erase(token);
                }
                catch (...) {
                    c->events.erase(token);
                    throw;
                }
            },
            static_cast<EventPriority>(priority), *c->plugin, ignore);
        if (!sub->handler) {
            throw std::runtime_error("Native listener registration rejected");
        }
        auto token = id();
        c->subscriptions.emplace(token, sub);
        return token;
    });
}
void JNICALL unsubscribe(JNIEnv *e, jclass, jlong owner, jlong token)
{
    boundary(e, [&] {
        auto c = context(owner);
        auto it = c->subscriptions.find(token);
        if (it == c->subscriptions.end()) {
            return;
        }
        auto sub = it->second;
        sub->active = false;
        sub->callback.reset();
        manager(*c).unregisterManagedEvent(*sub->handler);
        c->subscriptions.erase(it);
    });
}
jboolean JNICALL cancellation(JNIEnv *e, jclass, jlong owner, jlong token, jboolean write, jboolean value)
{
    return boundary(e, [&]() -> jboolean {
        auto c = context(owner);
        auto &entry = event(*c, token);
        auto *cancel = dynamic_cast<ICancellable *>(entry.event);
        if (!cancel) {
            throw std::runtime_error("Event is not cancellable");
        }
        if (write) {
            if (entry.monitor) {
                throw std::runtime_error("MONITOR listeners cannot modify events");
            }
            cancel->setCancelled(value);
        }
        return cancel->isCancelled();
    });
}
jstring JNICALL eventText(JNIEnv *e, jclass, jlong owner, jlong token, jboolean write, jstring value)
{
    return boundary(e, [&] {
        auto c = context(owner);
        auto &entry = event(*c, token);
        auto *chat = dynamic_cast<PlayerChatEvent *>(entry.event);
        if (!chat) {
            throw std::runtime_error("Not a chat event");
        }
        if (write) {
            if (entry.monitor) {
                throw std::runtime_error("MONITOR listeners cannot modify events");
            }
            chat->setMessage(text(e, value));
        }
        return string(e, chat->getMessage());
    });
}
jobjectArray JNICALL eventData(JNIEnv *e, jclass, jlong owner, jlong token, jint kind)
{
    return boundary(e, [&] {
        auto c = context(owner);
        auto &native = *event(*c, token).event;
        if (auto *move = dynamic_cast<PlayerMoveEvent *>(&native); move && (kind == 0 || kind == 1)) {
            return strings(e, location(kind == 0 ? move->getFrom() : move->getTo()));
        }
        if (auto *block = dynamic_cast<BlockEvent *>(&native); block && kind == 2) {
            return strings(e, location(block->getBlock().getLocation()));
        }
        if (auto *interact = dynamic_cast<PlayerInteractEvent *>(&native); interact && kind == 3) {
            return strings(e, {std::to_string(static_cast<int>(interact->getAction()))});
        }
        throw std::runtime_error("Invalid event query");
    });
}
class JavaCommand final : public Command {
public:
    std::weak_ptr<Context> owner;
    Global callback;
    jmethodID method;
    JavaCommand(const std::string &name, const std::string &description, const std::string &permission,
                const std::shared_ptr<Context> &c, Global ref, jmethodID execute)
        : Command(name, description, {"/" + name + " [args: message]"}, {},
                  permission.empty() ? std::vector<std::string>{} : std::vector<std::string>{permission}),
          owner(c), callback(std::move(ref)), method(execute)
    {
    }
    bool execute(CommandSender &native, const std::vector<std::string> &args) const override
    {
        auto c = owner.lock();
        if (!c || !c->active) {
            return false;
        }
        if (c->main.load() != std::this_thread::get_id()) {
            throw std::runtime_error("Java commands require server thread");
        }
        if (!c->plugin->isEnabled()) {
            return false;
        }
        if (!testPermission(native)) {
            return true;
        }
        auto ref = callback;
        if (!ref) {
            return false;
        }
        auto token = id();
        auto *commandPlayer = native.asPlayer();
        c->senders.emplace(
            token,
            PendingSender{&native, commandPlayer ? std::optional<UUID>{commandPlayer->getUniqueId()} : std::nullopt});
        try {
            Env env;
            auto argv = strings(env.e, args);
            auto result = env.e->CallBooleanMethod(ref->value, method, token, argv);
            callbackCheck(env.e);
            c->senders.erase(token);
            return result;
        }
        catch (const std::exception &error) {
            c->senders.erase(token);
            c->logger->error("Java command '{}': {}", getName(), error.what());
            return false;
        }
    }
};
jlong JNICALL command(JNIEnv *e, jclass, jlong owner, jstring name, jstring description, jstring permission,
                      jobject callback)
{
    return boundary(e, [&] {
        auto c = context(owner, true);
        if (!c->commands || !c->remove) {
            throw std::runtime_error("Native command map unavailable");
        }
        if (!callback) {
            throw std::invalid_argument("Null command callback");
        }
        auto label = text(e, name);
        if (label.empty() || label[0] < 'a' || label[0] > 'z' ||
            label.find_first_not_of("abcdefghijklmnopqrstuvwxyz0123456789_") != std::string::npos) {
            throw std::runtime_error("Invalid command name");
        }
        auto cls = e->GetObjectClass(callback);
        checked(e);
        auto method = e->GetMethodID(cls, "execute", "(J[Ljava/lang/String;)Z");
        checked(e);
        auto entry = std::make_shared<JavaCommand>(label, text(e, description), text(e, permission), c,
                                                   std::make_shared<Ref>(e, callback), method);
        if (!c->commands->registerCommand(entry)) {
            throw std::runtime_error("Command already registered or native registration failed");
        }
        auto token = id();
        c->registeredCommands.emplace(token, entry);
        return token;
    });
}
void JNICALL removeCommand(JNIEnv *e, jclass, jlong owner, jlong token)
{
    boundary(e, [&] {
        auto c = context(owner);
        auto it = c->registeredCommands.find(token);
        if (it == c->registeredCommands.end()) {
            return;
        }
        auto entry = it->second;
        c->remove(entry);
        static_cast<JavaCommand &>(*entry).callback.reset();
        c->registeredCommands.erase(it);
    });
}
jobjectArray JNICALL senderData(JNIEnv *e, jclass, jlong owner, jlong token)
{
    return boundary(e, [&] {
        auto c = context(owner);
        auto &s = sender(*c, token);
        auto *p = s.asPlayer();
        return strings(e, {s.getName(),
                           p               ? "player"
                           : s.asConsole() ? "console"
                                           : "other",
                           p ? std::to_string(playerId(*c, *p)) : "0"});
    });
}
void JNICALL translation(JNIEnv *e, jclass, jlong owner, jlong subject, jboolean isSender, jstring key,
                         jobjectArray arguments)
{
    boundary(e, [&] {
        auto c = context(owner);
        auto name = text(e, key);
        if (name.empty() || name.size() > 4096 || !arguments || e->GetArrayLength(arguments) > 64) {
            throw std::invalid_argument("Invalid translation");
        }
        std::vector<std::string> values;
        for (jsize i = 0; i < e->GetArrayLength(arguments); ++i) {
            auto value = static_cast<jstring>(e->GetObjectArrayElement(arguments, i));
            checked(e);
            values.push_back(text(e, value));
            e->DeleteLocalRef(value);
        }
        Message message = Translatable(std::move(name), std::move(values));
        if (isSender) {
            sender(*c, subject).sendMessage(message);
        }
        else {
            player(*c, subject).sendMessage(message);
        }
    });
}
void JNICALL senderMessage(JNIEnv *e, jclass, jlong owner, jlong token, jstring message)
{
    boundary(e, [&] {
        auto c = context(owner);
        sender(*c, token).sendMessage(text(e, message));
    });
}
jlong JNICALL schedule(JNIEnv *e, jclass, jlong owner, jobject callback, jlong delay, jlong period, jboolean async)
{
    return boundary(e, [&] {
        auto c = context(owner, true);
        if (delay < 0 || period < 0) {
            throw std::runtime_error("Negative scheduler ticks");
        }
        // Resource bounds, not a measured latency optimization. Terminal Java handles cache their state.
        std::erase_if(c->jobs, [](auto &entry) {
            auto &job = entry.second;
            auto task = job->task.lock();
            if (!job->active || !task || task->isCancelled()) {
                job->release(task && task->isCancelled());
                return true;
            }
            return false;
        });
        auto job = std::make_shared<Job>();
        if (c->liveJobs->load() >= 1024) {
            throw std::runtime_error("Native Java task quota exceeded (1024/plugin)");
        }
        if (activeJavaJobs.fetch_add(1) >= 8192) {
            --activeJavaJobs;
            throw std::runtime_error("Native Java task quota exceeded (8192/process)");
        }
        job->budget = c->liveJobs;
        ++*job->budget;
        job->callback = std::make_shared<Ref>(e, callback);
        auto cls = e->GetObjectClass(callback);
        checked(e);
        auto method = e->GetMethodID(cls, "run", "()V");
        checked(e);
        std::weak_ptr<Context> weak = c;
        auto action = [weak, job, method, period] {
            auto c = weak.lock();
            if (!c || !c->active || !job->active) {
                return;
            }
            Global ref;
            {
                std::lock_guard lock(job->mutex);
                ref = job->callback;
            }
            if (!ref || !job->active) {
                return;
            }
            try {
                Env env;
                env.e->CallVoidMethod(ref->value, method);
                callbackCheck(env.e);
            }
            catch (const std::exception &error) {
                c->logger->error("Java scheduled callback failed: {}", error.what());
            }
            if (!period) {
                job->release();
            }
        };
        auto &scheduler = c->server->getScheduler();
        std::shared_ptr<Task> native;
        if (async) {
            native = period ? scheduler.runTaskTimerAsync(*c->plugin, action, delay, period)
                            : scheduler.runTaskLaterAsync(*c->plugin, action, delay);
        }
        else {
            native = period ? scheduler.runTaskTimer(*c->plugin, action, delay, period)
                            : scheduler.runTaskLater(*c->plugin, action, delay);
        }
        if (!native) {
            throw std::runtime_error("Native scheduler rejected task");
        }
        job->task = native;
        auto token = id();
        c->jobs.emplace(token, job);
        return token;
    });
}
jboolean JNICALL task(JNIEnv *e, jclass, jlong owner, jlong token, jboolean cancel)
{
    return boundary(e, [&]() -> jboolean {
        auto c = context(owner);
        auto it = c->jobs.find(token);
        if (it == c->jobs.end()) {
            throw std::runtime_error("Invalid task handle");
        }
        auto job = it->second;
        auto native = job->task.lock();
        if (cancel) {
            job->release(true);
            if (native) {
                native->cancel();
            }
        }
        return job->cancelled || (native && native->isCancelled());
    });
}
void JNICALL cancelTasks(JNIEnv *e, jclass, jlong owner)
{
    boundary(e, [&] {
        auto c = context(owner);
        for (auto &[token, job] : c->jobs) {
            job->release(true);
        }
        c->server->getScheduler().cancelTasks(*c->plugin);
    });
}
}  // namespace

void openGameplay(jlong token, Plugin &plugin, CommandMap *commands, RemoveCommand remove)
{
    if (!plugin.getServer().isPrimaryThread()) {
        throw std::runtime_error("Gameplay lifecycle requires current server thread");
    }
    std::lock_guard lock(registryMutex);
    if (auto it = contexts.find(token); it != contexts.end()) {
        // BDS loads on Main, then enables on Server. Tasks/listeners cannot exist before enable.
        it->second->main = std::this_thread::get_id();
        return;
    }
    auto c = std::make_shared<Context>();
    c->plugin = &plugin;
    c->server = &plugin.getServer();
    c->logger = &plugin.getLogger();
    c->commands = commands;
    c->remove = std::move(remove);
    c->main = std::this_thread::get_id();
    contexts.emplace(token, c);
}
void activateGameplay(jlong token)
{
    context(token)->enabledPhase = true;
}
void closeGameplay(jlong token)
{
    std::shared_ptr<Context> c;
    {
        std::lock_guard lock(registryMutex);
        auto it = contexts.find(token);
        if (it == contexts.end()) {
            return;
        }
        c = it->second;
        c->active = false;
        if (c->bridge->enabled.exchange(false)) {
            --activeBridgeProfiles;
        }
        contexts.erase(it);
    }
    closeEcosystem(*c);
    closeNetwork(*c);
    closeAdvanced(*c);
    for (auto &[id, sub] : c->subscriptions) {
        sub->active = false;
        sub->callback.reset();
        try {
            manager(*c).unregisterManagedEvent(*sub->handler);
        }
        catch (const std::exception &error) {
            c->logger->error("Java listener cleanup failed: {}", error.what());
        }
    }
    c->subscriptions.clear();
    for (auto &[id, entry] : c->registeredCommands) {
        try {
            c->remove(entry);
        }
        catch (const std::exception &error) {
            c->logger->error("Java command cleanup failed: {}", error.what());
        }
        static_cast<JavaCommand &>(*entry).callback.reset();
    }
    c->registeredCommands.clear();
    for (auto &[id, job] : c->jobs) {
        job->release(true);
    }
    try {
        c->server->getScheduler().cancelTasks(*c->plugin);
    }
    catch (const std::exception &error) {
        c->logger->error("Java task cleanup failed: {}", error.what());
    }
    c->jobs.clear();
}
void registerGameplay(JNIEnv *e, JavaVM *vm)
{
    jvm = vm;
    initializeStringCodec(e);
    auto cls = e->FindClass("dev/zernyth/runtime/Native");
    callbackCheck(e);
#define METHOD(name, signature) \
    {const_cast<char *>(#name), const_cast<char *>(signature), reinterpret_cast<void *>(name)}
    JNINativeMethod methods[] = {
        METHOD(players, "(J)[J"),
        METHOD(playerData, "(JJI)[Ljava/lang/String;"),
        METHOD(playerNumber, "(JJI)I"),
        METHOD(playerAction, "(JJILjava/lang/String;[D)Z"),
        METHOD(permission, "(JJLjava/lang/String;Z)Z"),
        METHOD(subscribe, "(JLjava/lang/String;IZLdev/zernyth/runtime/Native$EventCallback;)J"),
        METHOD(unsubscribe, "(JJ)V"),
        METHOD(cancellation, "(JJZZ)Z"),
        METHOD(eventText, "(JJZLjava/lang/String;)Ljava/lang/String;"),
        METHOD(eventData, "(JJI)[Ljava/lang/String;"),
        METHOD(
            command,
            "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ldev/zernyth/runtime/Native$CommandCallback;)J"),
        METHOD(removeCommand, "(JJ)V"),
        METHOD(senderData, "(JJ)[Ljava/lang/String;"),
        METHOD(senderMessage, "(JJLjava/lang/String;)V"),
        METHOD(translation, "(JJZLjava/lang/String;[Ljava/lang/String;)V"),
        METHOD(schedule, "(JLjava/lang/Runnable;JJZ)J"),
        METHOD(task, "(JJZ)Z"),
        METHOD(cancelTasks, "(J)V")};
#undef METHOD
    if (e->RegisterNatives(cls, methods, static_cast<jint>(std::size(methods))) != JNI_OK) {
        callbackCheck(e);
        throw std::runtime_error("Gameplay RegisterNatives failed");
    }
    registerAdvanced(e);
    registerNetwork(e);
    registerEcosystem(e);
    registerPerformance(e);
}
}  // namespace endstone::core::java
