cmake_minimum_required(VERSION 3.15)
project(endstone_test LANGUAGES CXX)

endstone_add_plugin(test_plugin test_plugin.cpp)
set_target_properties(test_plugin PROPERTIES LIBRARY_OUTPUT_DIRECTORY "plugins")
set_target_properties(test_plugin PROPERTIES RUNTIME_OUTPUT_DIRECTORY "plugins")

add_executable(endstone_test
        bedrock/test_actor_definition_identifier.cpp
        bedrock/test_binary_stream.cpp
        bedrock/test_block_pos.cpp
        bedrock/test_check.cpp
        bedrock/test_function_ref.cpp
        bedrock/test_hashed_string.cpp
        bedrock/test_sem_version.cpp
        bedrock/test_spin_lock.cpp
        bedrock/test_static_optimized_string.cpp
        bedrock/test_uuid.cpp
        test_identifier.cpp
        test_nbt.cpp
        test_base64.cpp
        test_command_lexer.cpp
        test_command_usage_parser.cpp
        test_cpp_plugin_loader.cpp
        test_logger_factory.cpp
        test_player_ban_list.cpp
        test_plugin_manager.cpp
        test_scheduler.cpp
        test_service_manager.cpp
        test_text_formatter.cpp
        test_thread_pool_executor.cpp
        test_uuid.cpp
        test_vector.cpp
)
add_dependencies(endstone_test test_plugin)
if (ENDSTONE_ENABLE_JAVA)
    find_package(JNI REQUIRED COMPONENTS JVM)
    target_include_directories(endstone_test PRIVATE ${JNI_INCLUDE_DIRS})
    target_sources(endstone_test PRIVATE test_java_plugin_loader.cpp test_java_gameplay.cpp test_java_advanced.cpp test_java_network.cpp test_java_ecosystem.cpp test_java_performance.cpp test_java_async.cpp)
endif ()
target_link_libraries(endstone_test PRIVATE endstone::core GTest::gtest_main GTest::gmock_main)
# CppPluginLoaderTest cases share the plugins/.local shadow-copy dir, whose
# cleanup races when ctest runs them in parallel; serialize just those.
gtest_discover_tests(endstone_test
        DISCOVERY_TIMEOUT 30
        TEST_FILTER "CppPluginLoaderTest.*"
        PROPERTIES RESOURCE_LOCK cpp_plugin_loader_local)
gtest_discover_tests(endstone_test
        DISCOVERY_TIMEOUT 30
        TEST_FILTER "-CppPluginLoaderTest.*:Java*")
if (ENDSTONE_ENABLE_JAVA)
    # JVM tests also instantiate LoggerFactory. Run alone to avoid log-rotation races
    # with unrelated test processes that share tests/logs/latest.log.
    gtest_discover_tests(endstone_test DISCOVERY_TIMEOUT 30 TEST_FILTER "Java*" PROPERTIES RUN_SERIAL TRUE TIMEOUT 60)
endif ()
