#include "endstone/core/plugin/java_gameplay_internal.h"
namespace endstone::core::java {
using namespace details;
namespace {
jlongArray JNICALL performance(JNIEnv *e, jclass, jlong owner, jint mode)
{
    return boundary(e, [&] {
        auto c = context(owner, true);
        if (mode < -1 || mode > 1) {
            throw std::invalid_argument("Invalid profiling mode");
        }
        auto stats = c->bridge;
        if (mode == 1 && !stats->enabled.load()) {
            ++activeBridgeProfiles;
            stats->enabled = true;
        }
        if (mode == 0 && stats->enabled.exchange(false)) {
            --activeBridgeProfiles;
        }
        jlong values[] = {static_cast<jlong>(stats->calls.load()), static_cast<jlong>(stats->nanos.load()),
                          static_cast<jlong>(stats->maximum.load()), static_cast<jlong>(stats->errors.load())};
        auto out = e->NewLongArray(4);
        checked(e);
        e->SetLongArrayRegion(out, 0, 4, values);
        checked(e);
        return out;
    });
}
void JNICALL validate(JNIEnv *e, jclass, jlong owner)
{
    boundary(e, [&] { (void)context(owner); });
}
}  // namespace
void registerPerformance(JNIEnv *e)
{
    auto cls = e->FindClass("dev/zernyth/runtime/Native");
    callbackCheck(e);
    JNINativeMethod methods[] = {
        {const_cast<char *>("performance"), const_cast<char *>("(JI)[J"), reinterpret_cast<void *>(performance)},
        {const_cast<char *>("validate"), const_cast<char *>("(J)V"), reinterpret_cast<void *>(validate)}};
    if (e->RegisterNatives(cls, methods, 2) != JNI_OK) {
        callbackCheck(e);
        throw std::runtime_error("Performance JNI registration failed");
    }
}
}  // namespace endstone::core::java
