#include "endstone/core/plugin/java_gameplay_internal.h"
#include "endstone/core/plugin/service_manager.h"
#include "endstone/plugin/service.h"

namespace endstone::core::java::details {
struct JavaService final : Service {
    jlong token;
    std::weak_ptr<Context> owner;
    Global type, provider;
    bool active = true;
};
struct EcosystemState {
    std::unordered_map<jlong, std::shared_ptr<JavaService>> services;
};
namespace {
std::unordered_map<jlong, std::weak_ptr<JavaService>> serviceTokens;  // Server thread only.
void removeService(Context &c, jlong token)
{
    if (!c.ecosystem) {
        return;
    }
    auto it = c.ecosystem->services.find(token);
    if (it == c.ecosystem->services.end()) {
        return;
    }
    auto service = it->second;
    service->active = false;
    service->provider.reset();
    service->type.reset();
    serviceTokens.erase(token);
    c.server->getServiceManager().unregister(*service);
    c.ecosystem->services.erase(it);
}
}  // namespace
void closeEcosystem(Context &c)
{
    if (!c.ecosystem) {
        return;
    }
    while (!c.ecosystem->services.empty()) {
        removeService(c, c.ecosystem->services.begin()->first);
    }
    c.ecosystem.reset();
}
}  // namespace endstone::core::java::details
namespace endstone::core::java {
using namespace details;
namespace {
jobjectArray JNICALL ecosystemInfo(JNIEnv *e, jclass, jlong owner)
{
    return boundary(e, [&] {
        auto c = context(owner);
        auto folder = c->plugin->getDataFolder().u8string();
        return strings(e, {std::string(folder.begin(), folder.end()), c->plugin->getName(),
                           c->plugin->getDescription().getVersion()});
    });
}
jlongArray JNICALL ecosystemCounts(JNIEnv *e, jclass, jlong owner)
{
    return boundary(e, [&] {
        auto c = context(owner);
        jlong activeTasks = 0;
        for (auto &[token, job] : c->jobs) {
            if (job->active && !job->cancelled) {
                ++activeTasks;
            }
        }
        jlong values[] = {static_cast<jlong>(c->subscriptions.size()), networkListenerCount(*c),
                          static_cast<jlong>(c->registeredCommands.size()), activeTasks,
                          c->ecosystem ? static_cast<jlong>(c->ecosystem->services.size()) : 0};
        auto out = e->NewLongArray(5);
        checked(e);
        e->SetLongArrayRegion(out, 0, 5, values);
        checked(e);
        return out;
    });
}
jlong JNICALL serviceRegister(JNIEnv *e, jclass, jlong owner, jstring name, jclass type, jobject provider,
                              jint priority)
{
    return boundary(e, [&] {
        auto c = context(owner, true);
        if (!type || !provider || priority < 0 || priority > 4 || !e->IsInstanceOf(provider, type)) {
            throw std::invalid_argument("Invalid service registration");
        }
        checked(e);
        if (!c->ecosystem) {
            c->ecosystem = std::make_shared<EcosystemState>();
        }
        if (c->ecosystem->services.size() >= 256) {
            throw std::runtime_error("Service registration limit (256)");
        }
        auto service = std::make_shared<JavaService>();
        service->token = id();
        service->owner = c;
        service->type = std::make_shared<Ref>(e, type);
        service->provider = std::make_shared<Ref>(e, provider);
        auto key = "zernyth.java:" + text(e, name);
        c->server->getServiceManager().registerService(key, service, *c->plugin,
                                                       static_cast<ServicePriority>(priority));
        c->ecosystem->services.emplace(service->token, service);
        serviceTokens[service->token] = service;
        return service->token;
    });
}
void JNICALL serviceRemove(JNIEnv *e, jclass, jlong owner, jlong token)
{
    boundary(e, [&] {
        auto c = context(owner);
        removeService(*c, token);
    });
}
jlongArray JNICALL serviceFind(JNIEnv *e, jclass, jlong owner, jstring name, jclass type)
{
    return boundary(e, [&] {
        auto c = context(owner, true);
        auto *manager = dynamic_cast<EndstoneServiceManager *>(&c->server->getServiceManager());
        if (!manager) {
            throw std::runtime_error("Native Endstone service manager required");
        }
        std::vector<jlong> tokens;
        for (auto &raw : manager->getProviders("zernyth.java:" + text(e, name))) {
            auto service = std::dynamic_pointer_cast<JavaService>(raw);
            if (!service) {
                throw std::runtime_error("Conflicting non-Java service provider");
            }
            auto providerOwner = service->owner.lock();
            if (!providerOwner || !providerOwner->active || !service->active || !providerOwner->plugin->isEnabled()) {
                continue;
            }
            if (!e->IsSameObject(service->type->value, type)) {
                throw std::runtime_error("Service interface class-loader mismatch: declare imports/exports");
            }
            tokens.push_back(service->token);
        }
        auto out = e->NewLongArray(static_cast<jsize>(tokens.size()));
        checked(e);
        e->SetLongArrayRegion(out, 0, static_cast<jsize>(tokens.size()), tokens.data());
        checked(e);
        return out;
    });
}
jobject JNICALL serviceProvider(JNIEnv *e, jclass, jlong owner, jlong token)
{
    return boundary(e, [&] {
        auto caller = context(owner, true);
        auto it = serviceTokens.find(token);
        auto service = it == serviceTokens.end() ? nullptr : it->second.lock();
        auto providerOwner = service ? service->owner.lock() : nullptr;
        if (!service || !service->active || !providerOwner || !providerOwner->active ||
            providerOwner->server != caller->server || !providerOwner->plugin->isEnabled()) {
            throw std::runtime_error("Service provider is no longer available");
        }
        auto value = e->NewLocalRef(service->provider->value);
        checked(e);
        return value;
    });
}
}  // namespace
void registerEcosystem(JNIEnv *e)
{
    auto cls = e->FindClass("dev/zernyth/runtime/Native");
    callbackCheck(e);
#define METHOD(name, signature) \
    {const_cast<char *>(#name), const_cast<char *>(signature), reinterpret_cast<void *>(name)}
    JNINativeMethod methods[] = {METHOD(ecosystemInfo, "(J)[Ljava/lang/String;"),
                                 METHOD(ecosystemCounts, "(J)[J"),
                                 METHOD(serviceRegister, "(JLjava/lang/String;Ljava/lang/Class;Ljava/lang/Object;I)J"),
                                 METHOD(serviceRemove, "(JJ)V"),
                                 METHOD(serviceFind, "(JLjava/lang/String;Ljava/lang/Class;)[J"),
                                 METHOD(serviceProvider, "(JJ)Ljava/lang/Object;")};
#undef METHOD
    if (e->RegisterNatives(cls, methods, static_cast<jint>(std::size(methods))) != JNI_OK) {
        callbackCheck(e);
        throw std::runtime_error("Ecosystem JNI registration failed");
    }
}
}  // namespace endstone::core::java
