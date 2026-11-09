#include "endstone/core/plugin/java_plugin_loader.h"

#include <jni.h>

#include <cstdlib>
#include <limits>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <system_error>
#include <unordered_map>

#include "endstone/core/plugin/java_gameplay.h"
#include "endstone/zernyth_version.h"
#ifdef _WIN32
#include <Windows.h>
#else
#include <dlfcn.h>
#endif

namespace endstone::core {
namespace {
// Process lifetime by design: JNI does not support VM recreation. Never unload libjvm.
JavaVM *vm = nullptr;
// JDK 25's jni.h exposes JNI_VERSION_24 as its latest JNI revision.
// Java 25 class files and the CMake/Gradle toolchains enforce the runtime baseline.
jclass bootstrap = nullptr;
std::once_flag vm_once;
std::string vm_error;
std::mutex handles_mutex;
std::unordered_map<jlong, Plugin *> handles;
jlong next_handle = 1;

void check(JNIEnv *env)
{
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();  // Full Java stack on stderr; native caller adds plugin context.
        env->ExceptionClear();
        throw std::runtime_error("Java exception (see JVM stderr stack trace)");
    }
}
class Frame {
public:
    JNIEnv *env = nullptr;
    bool attached = false;
    Frame()
    {
        auto result = vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_24);
        if (result == JNI_EDETACHED) {
            if (vm->AttachCurrentThreadAsDaemon(reinterpret_cast<void **>(&env), nullptr) != JNI_OK) {
                throw std::runtime_error("Cannot attach native thread to JVM");
            }
            attached = true;
        }
        else if (result != JNI_OK) {
            throw std::runtime_error("Unsupported JNI version");
        }
        if (env->PushLocalFrame(32) != JNI_OK) {
            env->ExceptionClear();
            if (attached) {
                vm->DetachCurrentThread();
            }
            throw std::runtime_error("Cannot allocate JNI local frame");
        }
    }
    ~Frame()
    {
        env->PopLocalFrame(nullptr);
        if (attached) {
            vm->DetachCurrentThread();
        }
    }
};
std::string string(JNIEnv *env, jstring value)
{
    if (!value) {
        throw std::runtime_error("Unexpected null Java string");
    }
    auto *chars = env->GetStringUTFChars(value, nullptr);
    check(env);
    if (!chars) {
        throw std::runtime_error("Cannot access Java string");
    }
    try {
        std::string result(chars);
        env->ReleaseStringUTFChars(value, chars);
        return result;
    }
    catch (...) {
        env->ReleaseStringUTFChars(value, chars);
        throw;
    }
}
void JNICALL bridgeLog(JNIEnv *env, jclass, jlong handle, jstring message) noexcept
{
    try {
        std::lock_guard lock(handles_mutex);
        auto it = handles.find(handle);
        if (it == handles.end()) {
            throw std::runtime_error("Expired Java plugin handle");
        }
        auto &plugin = *it->second;
        // LoggerFactory loggers are thread-safe; keep the registry lock until this access ends
        // so disable/destruction cannot free the plugin while an async task is logging.
        plugin.getLogger().info("{}", string(env, message));
    }
    catch (const std::exception &e) {
        if (!env->ExceptionCheck()) {
            auto type = env->FindClass("java/lang/IllegalStateException");
            if (type) {
                env->ThrowNew(type, e.what());
            }
        }
    }
    catch (...) {
        if (!env->ExceptionCheck()) {
            auto type = env->FindClass("java/lang/IllegalStateException");
            if (type) {
                env->ThrowNew(type, "Native bridge failure");
            }
        }
    }
}
void initialize()
{
    std::call_once(vm_once, [] {
        try {
            auto *library = std::getenv("ZERNYTH_JVM_LIBRARY");
            auto *classpath = std::getenv("ZERNYTH_JAVA_CLASSPATH");
            if (!library || !classpath) {
                throw std::runtime_error("Set ZERNYTH_JVM_LIBRARY and ZERNYTH_JAVA_CLASSPATH");
            }
#ifdef _WIN32
            auto module = LoadLibraryA(library);
            if (!module) {
                const auto error = GetLastError();
                throw std::runtime_error("Cannot load configured JVM library: Windows error " + std::to_string(error) +
                                         " (" + std::system_category().message(error) +
                                         "). Check JDK bin dependencies and PATH.");
            }
            auto create = module ? reinterpret_cast<jint(JNICALL *)(JavaVM **, void **, void *)>(
                                       GetProcAddress(module, "JNI_CreateJavaVM"))
                                 : nullptr;
#else
            auto module = dlopen(library, RTLD_NOW | RTLD_GLOBAL);
            auto create = module ? reinterpret_cast<jint (JNICALL *)(JavaVM **, void **, void *)>(dlsym(module, "JNI_CreateJavaVM")) : nullptr;
#endif
            if (!create)
                throw std::runtime_error("Cannot load JNI_CreateJavaVM from configured JVM library");
            std::string cp = std::string("-Djava.class.path=") + classpath;
            JavaVMOption option{cp.data(), nullptr};
            JavaVMInitArgs args{};
            args.version = JNI_VERSION_24;
            args.nOptions = 1;
            args.options = &option;
            args.ignoreUnrecognized = JNI_FALSE;
            JNIEnv *env = nullptr;
            auto status = create(&vm, reinterpret_cast<void **>(&env), &args);
            if (status != JNI_OK)
                throw std::runtime_error("JVM creation failed, JNI status " + std::to_string(status));
            Frame frame;
            auto local = env->FindClass("dev/zernyth/runtime/Bootstrap");
            check(env);
            bootstrap = static_cast<jclass>(env->NewGlobalRef(local));
            check(env);
            if (!bootstrap)
                throw std::runtime_error("Cannot retain bootstrap class");
            auto version_method = env->GetStaticMethodID(bootstrap, "runtimeVersion", "()Ljava/lang/String;");
            check(env);
            auto runtime_version = static_cast<jstring>(env->CallStaticObjectMethod(bootstrap, version_method));
            check(env);
            if (string(env, runtime_version) != ZERNYTH_ENGINE_VERSION)
                throw std::runtime_error(
                    "Zernyth native/API/runtime version mismatch; install one matching distribution");
            JNINativeMethod method{const_cast<char *>("log"), const_cast<char *>("(JLjava/lang/String;)V"),
                                   reinterpret_cast<void *>(bridgeLog)};
            if (env->RegisterNatives(bootstrap, &method, 1) != JNI_OK) {
                check(env);
                throw std::runtime_error("RegisterNatives failed");
            }
            java::registerGameplay(env, vm);
        }
        catch (const std::exception &e) {
            if (vm && bootstrap) {
                JNIEnv *env = nullptr;
                if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_24) == JNI_OK)
                    env->DeleteGlobalRef(bootstrap);
                bootstrap = nullptr;
            }
            vm_error = e.what();
        }
    });
    if (!vm_error.empty()) {
        throw std::runtime_error(vm_error);
    }
}
std::vector<std::string> split(const std::string &s)
{
    std::vector<std::string> result;
    std::istringstream in(s);
    for (std::string item; std::getline(in, item, ',');) {
        result.push_back(item);
    }
    return result;
}
class JavaPlugin final : public Plugin {
public:
    PluginDescription description;
    std::string path;
    jobject instance = nullptr;
    jlong handle;
    bool recreate = false;
    CommandMap *commands;
    java::RemoveCommand remove;
    JavaPlugin(PluginDescription d, std::string p, CommandMap *map, java::RemoveCommand release)
        : description(std::move(d)), path(std::move(p)), commands(map), remove(std::move(release))
    {
        std::lock_guard lock(handles_mutex);
        if (next_handle == (std::numeric_limits<jlong>::max)()) {
            throw std::runtime_error("Java handle space exhausted");
        }
        handle = next_handle++;
    }
    ~JavaPlugin() override
    {
        java::closeGameplay(handle);
        {
            std::lock_guard lock(handles_mutex);
            handles.erase(handle);
        }
        if (instance) {
            try {
                Frame frame;
                try {
                    invoke(frame.env, "close");
                }
                catch (...) {
                }
                frame.env->DeleteGlobalRef(instance);
            }
            catch (...) {
            }  // VM remains alive until process exit.
        }
    }
    const PluginDescription &getDescription() const override { return description; }
    void invoke(JNIEnv *env, const char *method)
    {
        auto id = env->GetMethodID(bootstrap, method, "()V");
        check(env);
        env->CallVoidMethod(instance, id);
        check(env);
    }
    void onLoad() override
    {
        if (!getServer().isPrimaryThread()) {
            throw std::runtime_error("Java lifecycle requires server thread");
        }
        {
            std::lock_guard lock(handles_mutex);
            handles.emplace(handle, this);
        }
        try {
            java::openGameplay(handle, *this, commands, remove);
            Frame f;
            auto ctor = f.env->GetMethodID(bootstrap, "<init>", "(Ljava/lang/String;J)V");
            check(f.env);
            auto file = f.env->NewStringUTF(path.c_str());
            check(f.env);
            auto local = f.env->NewObject(bootstrap, ctor, file, handle);
            check(f.env);
            instance = f.env->NewGlobalRef(local);
            check(f.env);
            if (!instance) {
                throw std::runtime_error("Cannot retain Java plugin");
            }
            invoke(f.env, "load");
        }
        catch (...) {
            java::closeGameplay(handle);
            {
                std::lock_guard lock(handles_mutex);
                handles.erase(handle);
            }
            if (instance) {
                Frame f;
                try {
                    invoke(f.env, "close");
                }
                catch (...) {
                }
                f.env->DeleteGlobalRef(instance);
                instance = nullptr;
            }
            throw;
        }
    }
    void onEnable() override
    {
        try {
            if (recreate) {
                Frame f;
                if (instance) {
                    invoke(f.env, "close");
                    f.env->DeleteGlobalRef(instance);
                    instance = nullptr;
                }
                {
                    std::lock_guard lock(handles_mutex);
                    handles.erase(handle);
                    if (next_handle == (std::numeric_limits<jlong>::max)()) {
                        throw std::runtime_error("Java handle space exhausted");
                    }
                    handle = next_handle++;
                }
                recreate = false;
                onLoad();
            }
            {
                std::lock_guard lock(handles_mutex);
                handles[handle] = this;
            }
            java::openGameplay(handle, *this, commands, remove);
            java::activateGameplay(handle);
            lifecycle("enable");
        }
        catch (...) {
            // Plugin::setEnabled marks enabled before entering onEnable; undo through the manager
            // so its scheduler/service/event cleanup also runs for a partial enable.
            getServer().getPluginManager().disablePlugin(*this);
            throw;
        }
    }
    void onDisable() override
    {
        recreate = true;
        try {
            if (instance) {
                lifecycle("disable");
            }
        }
        catch (...) {
            java::closeGameplay(handle);
            releaseInstance();
            std::lock_guard lock(handles_mutex);
            handles.erase(handle);
            throw;
        }
        java::closeGameplay(handle);
        releaseInstance();
        std::lock_guard lock(handles_mutex);
        handles.erase(handle);
    }
    void releaseInstance() noexcept
    {
        if (!instance) {
            return;
        }
        try {
            Frame f;
            try {
                invoke(f.env, "close");
            }
            catch (...) {
            }
            f.env->DeleteGlobalRef(instance);
            instance = nullptr;
        }
        catch (...) {
        }
    }
    void lifecycle(const char *name)
    {
        if (!getServer().isPrimaryThread()) {
            throw std::runtime_error("Java lifecycle requires server thread");
        }
        Frame f;
        invoke(f.env, name);
    }
};
}  // namespace
JavaPluginLoader::JavaPluginLoader(Server &server, CommandMap *commands, java::RemoveCommand remove)
    : PluginLoader(server), commands_(commands), remove_(std::move(remove))
{
}
JavaPluginLoader::~JavaPluginLoader() = default;
Plugin *JavaPluginLoader::loadPlugin(std::string file)
{
    try {
        if (!server_.isPrimaryThread()) {
            throw std::runtime_error("Java loading requires server thread");
        }
        initialize();
        Frame f;
        auto describe = f.env->GetStaticMethodID(bootstrap, "describe", "(Ljava/lang/String;)[Ljava/lang/String;");
        check(f.env);
        auto path = std::filesystem::absolute(file).string();
        auto argument = f.env->NewStringUTF(path.c_str());
        check(f.env);
        auto fields = static_cast<jobjectArray>(f.env->CallStaticObjectMethod(bootstrap, describe, argument));
        check(f.env);
        if (!fields || f.env->GetArrayLength(fields) != 6) {
            throw std::runtime_error("Invalid Java descriptor");
        }
        std::vector<std::string> d;
        for (int i = 0; i < 6; ++i) {
            auto value = static_cast<jstring>(f.env->GetObjectArrayElement(fields, i));
            check(f.env);
            d.push_back(string(f.env, value));
        }
        PluginDescription description(d[0], d[1], "", PluginLoadOrder::PostWorld, {}, {}, "", "", {}, split(d[3]),
                                      split(d[4]), split(d[5]));
        return plugins_.emplace_back(std::make_unique<JavaPlugin>(std::move(description), path, commands_, remove_))
            .get();
    }
    catch (const std::exception &e) {
        server_.getLogger().error("Cannot load Java plugin '{}': {}", file, e.what());
        return nullptr;
    }
}
}  // namespace endstone::core
