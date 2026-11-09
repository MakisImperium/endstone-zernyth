@echo off
setlocal
rem Existing ctest executable, existing JDK 25 home, optional third argument: check.
if not exist "%~1" exit /b 2
if not exist "%~2\bin\server\jvm.dll" exit /b 2
set "ZERNYTH_BENCHMARK_ROOT=%~dp0.."
set /p ZERNYTH_VERSION=<"%~dp0..\zernyth-version.txt"
set "ZERNYTH_JVM_LIBRARY=%~2\bin\server\jvm.dll"
set "PATH=%~2\bin;%PATH%"
set "ZERNYTH_JAVA_CLASSPATH=%ZERNYTH_BENCHMARK_ROOT%\java\api\build\libs\api-%ZERNYTH_VERSION%.jar;%ZERNYTH_BENCHMARK_ROOT%\java\runtime\build\libs\runtime-%ZERNYTH_VERSION%.jar;%ZERNYTH_BENCHMARK_ROOT%\java\runtime\build\libs\runtime-%ZERNYTH_VERSION%-benchmark-support.jar"
set "ZERNYTH_BENCHMARK_JAR=%ZERNYTH_BENCHMARK_ROOT%\java\runtime\build\libs\runtime-%ZERNYTH_VERSION%-benchmark-plugin.jar"
set "ZERNYTH_PERF_OUTPUT=%ZERNYTH_BENCHMARK_ROOT%\build\native-benchmark.json"
set "JAVA_TOOL_OPTIONS=-Xms256m -Xmx512m"
if /I "%~3"=="check" set "JAVA_TOOL_OPTIONS=-Xms256m -Xmx512m -Xcheck:jni"
"%~1" --test-dir "%ZERNYTH_BENCHMARK_ROOT%\build\RelWithDebInfo" --output-on-failure -R "^JavaPerformanceTest\."
exit /b %errorlevel%
