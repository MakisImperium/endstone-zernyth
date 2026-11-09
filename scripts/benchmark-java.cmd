@echo off
setlocal
rem Existing Gradle executable, existing JDK 25 home, optional third argument: managed.
if not exist "%~1" exit /b 2
if not exist "%~2\bin\java.exe" exit /b 2
set "JAVA_HOME=%~2"
set "ZERNYTH_BENCHMARK_TASK=:runtime:benchmarkJava"
if /I "%~3"=="managed" set "ZERNYTH_BENCHMARK_TASK=:runtime:benchmarkManaged"
pushd "%~dp0..\java"
if errorlevel 1 exit /b 2
call "%~1" --offline --no-daemon -PzernythJavaVendor=any %ZERNYTH_BENCHMARK_TASK%
set "zernythBenchmarkExit=%errorlevel%"
popd
exit /b %zernythBenchmarkExit%
