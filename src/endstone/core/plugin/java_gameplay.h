#pragma once
#include <jni.h>

#include <functional>
#include <memory>

#include "endstone/command/command_map.h"
#include "endstone/plugin/plugin.h"

namespace endstone::core::java {
using RemoveCommand = std::function<void(const std::shared_ptr<Command> &)>;
void registerGameplay(JNIEnv *env, JavaVM *vm);
void registerAdvanced(JNIEnv *env);
void registerNetwork(JNIEnv *env);
void registerEcosystem(JNIEnv *env);
void registerPerformance(JNIEnv *env);
void openGameplay(jlong id, Plugin &plugin, CommandMap *commands, RemoveCommand remove);
void activateGameplay(jlong id);
void closeGameplay(jlong id);
}  // namespace endstone::core::java
