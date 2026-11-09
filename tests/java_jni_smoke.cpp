// Standalone Invocation API smoke test. This does NOT link the Endstone Core bridge.
// Build manually with JNI headers; no BDS process or external test libraries needed.
#include <jni.h>
#include <windows.h>

#include <atomic>
#include <iostream>
#include <stdexcept>
#include <string>
#include <thread>

namespace {
std::atomic<int> logs{0};
void check(JNIEnv *env)
{
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        throw std::runtime_error("Unexpected Java exception");
    }
}
void JNICALL bridgeLog(JNIEnv *env, jclass, jlong handle, jstring message)
{
    // Test callback only, not a substitute for testing production native handle validation.
    if (handle != 42 || !message) {
        auto type = env->FindClass("java/lang/IllegalStateException");
        if (type) {
            env->ThrowNew(type, "Invalid smoke-test handle");
        }
        return;
    }
    ++logs;
}
}  // namespace
int main(int argc, char **argv)
{
    if (argc != 4) {
        std::cerr << "Expected JVM library, classpath, example JAR\n";
        return 2;
    }
    try {
        auto library = LoadLibraryA(argv[1]);
        if (!library) {
            throw std::runtime_error("Cannot load JVM");
        }
        auto create =
            reinterpret_cast<jint(JNICALL *)(JavaVM **, void **, void *)>(GetProcAddress(library, "JNI_CreateJavaVM"));
        if (!create) {
            throw std::runtime_error("Missing invocation entry point");
        }
        std::string cp = std::string("-Djava.class.path=") + argv[2];
        JavaVMOption options[] = {{cp.data(), nullptr}, {const_cast<char *>("-Xcheck:jni"), nullptr}};
        JavaVMInitArgs args{};
        args.version = JNI_VERSION_24;
        args.nOptions = 2;
        args.options = options;
        JavaVM *vm = nullptr;
        JNIEnv *env = nullptr;
        auto status = create(&vm, reinterpret_cast<void **>(&env), &args);
        if (status != JNI_OK) {
            throw std::runtime_error("CreateJavaVM status " + std::to_string(status));
        }
        auto type = env->FindClass("dev/zernyth/runtime/Bootstrap");
        check(env);
        JNINativeMethod method{const_cast<char *>("log"), const_cast<char *>("(JLjava/lang/String;)V"),
                               reinterpret_cast<void *>(bridgeLog)};
        status = env->RegisterNatives(type, &method, 1);
        check(env);
        if (status != JNI_OK) {
            throw std::runtime_error("RegisterNatives failed");
        }
        auto ctor = env->GetMethodID(type, "<init>", "(Ljava/lang/String;J)V");
        check(env);
        auto path = env->NewStringUTF(argv[3]);
        check(env);
        auto plugin = env->NewObject(type, ctor, path, static_cast<jlong>(42));
        check(env);
        for (const char *name : {"load", "enable", "disable", "close"}) {
            auto id = env->GetMethodID(type, name, "()V");
            check(env);
            env->CallVoidMethod(plugin, id);
            check(env);
        }
        if (logs != 2) {
            throw std::runtime_error("Expected both HelloZernyth log callbacks");
        }
        auto describe = env->GetStaticMethodID(type, "describe", "(Ljava/lang/String;)[Ljava/lang/String;");
        check(env);
        auto missing = env->NewStringUTF("missing-smoke-test.jar");
        check(env);
        env->CallStaticObjectMethod(type, describe, missing);
        if (!env->ExceptionCheck()) {
            throw std::runtime_error("Missing JAR was accepted");
        }
        env->ExceptionClear();
        std::atomic<bool> attached{false};
        std::thread worker([&] {
            JNIEnv *other = nullptr;
            if (vm->GetEnv(reinterpret_cast<void **>(&other), JNI_VERSION_24) != JNI_EDETACHED) {
                return;
            }
            if (vm->AttachCurrentThreadAsDaemon(reinterpret_cast<void **>(&other), nullptr) != JNI_OK) {
                return;
            }
            attached = vm->DetachCurrentThread() == JNI_OK;
        });
        worker.join();
        if (!attached) {
            throw std::runtime_error("Thread attach/detach failed");
        }
        env->DeleteLocalRef(missing);
        env->DeleteLocalRef(plugin);
        env->DeleteLocalRef(path);
        env->DeleteLocalRef(type);
        // Smoke host owns its VM. Production deliberately keeps its VM until process exit.
        if (vm->DestroyJavaVM() != JNI_OK) {
            throw std::runtime_error("Smoke JVM shutdown failed");
        }
        std::cout << "JNI smoke passed: actual example JAR, lifecycle, 2 native logs, Java exception, thread "
                     "attachment, host VM shutdown.\n";
        return 0;
    }
    catch (const std::exception &e) {
        std::cerr << e.what() << '\n';
        return 1;
    }
}
