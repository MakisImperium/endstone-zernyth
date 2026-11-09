# Zernyth 0.6: asynchronous runtime and profiling

Java 25 LTS remains the minimum. Use Temurin 25, or the documented explicit local
Oracle 25 override. No downloads, additional server, plugin manager or embedded JVM
are introduced. CPU execution and the tick pulse use Endstone's existing scheduler.

## Execution and ownership

Obtain `getServer().getAsyncRuntime()` on the server thread during `onEnable()`.
`submitCpu`, `submitIo`, `submit`, `configure` and `cancelAll` require the live owning
server thread. CPU work uses Endstone workers. I/O work uses Java 25 virtual threads,
created only after admission and concurrency permits are available. Virtual threads
do not grant native API access. Player, world, entity, inventory, forms, services,
commands and native network views retain their previous thread/handle guards.

Only cancellation, immutable task/runtime snapshots and `returnToServer` are
worker-safe. A completion stage can execute its continuations on the completing
worker, including after disable for an already running task. Always use an explicit
server handoff for native work. Do not retain an ephemeral `CommandSender` for it.

```java
var runtime = getServer().getAsyncRuntime(); // initialize on server thread
TaskHandle work = runtime.submitIo(token -> {
    // Bounded external I/O needs its own connection/read timeouts.
    String result = readFromYourDatabase();
    token.throwIfCancellationRequested();
    runtime.returnToServer(() -> getLogger().info(result));
});
// Server or worker thread:
work.cancel();
TaskSnapshot state = work.snapshot();
```

The body is application code, not an SDK database helper. Rejection throws
`TaskRejectedException`; handle it explicitly, including when disable races a
handoff. No caller waits for free capacity. No automatic retry or silent task loss.

`submit(TaskKind, TaskAction, TaskOptions)` also supports `SERVER`, tick delays and
non-overlapping repeats. Repeat spacing starts after completion is observed and is
subject to native tick scheduling; it is not a wall-clock deadline. There are no
invented task priorities or promises about exact execution times.

## Limits and cancellation

Defaults per plugin: 256 outstanding tasks, 1 concurrent CPU task, 4 concurrent I/O
tasks, 32 server callbacks per pulse. Configure `RuntimeLimits` before outstanding
work exists. Its maxima are 4096 / 16 / 128 / 256 respectively. A pulse also stops
admitting further server callbacks after a 2 ms dispatch budget; one callback can
exceed it and cannot be preempted safely.

Process-wide defaults: 4096 outstanding, CPU `max(1,min(4,processors-1))`, I/O 32.
Set `-Dzernyth.async.globalOutstanding`, `-Dzernyth.async.globalCpu` and
`-Dzernyth.async.globalIo` before runtime initialization to adjust them (maxima
65536 / 64 / 1024). These properties bound managed work, not arbitrary threads that
plugins create themselves. Legacy JNI scheduler registrations additionally have
1024 live callback registrations/plugin and 8192/process limits; terminal records
are pruned on subsequent admission. These are callback bounds, not guarantees that
already cancelled, non-cooperative legacy code stopped executing.

Queued/dispatched tasks cancel immediately. Running tasks become `CANCEL_REQUESTED`;
only their actual exit produces `CANCELLED`. I/O virtual threads receive an interrupt;
CPU work receives a cooperative token. There is no `Thread.stop`, native interruption,
join on the server thread, or forced JVM restart. Non-cooperative work retains its
capacity and may retain its loader until it exits. `warnAfter` only diagnoses elapsed
wall time; it does not impose an execution deadline. Warnings aggregate first/every
N occurrences and never disable a plugin automatically.

Disable rejects new work before `onDisable`, cancels pending work, requests running
cancellation and removes the tick pulse. Native lifecycle cleanup remains in the
original plugin manager. Re-enable creates a new owner generation and classloader.
Old handles cannot submit into the new generation. Legacy `Scheduler.cancelTasks()`
also cancels managed work and removes its pulse; a later server-thread lookup starts
a fresh pulse for that still-enabled generation.

Task bodies set the plugin TCCL and restore the prior TCCL in `finally`. Managed I/O
threads disable inheritable thread-local copying. Plugins remain responsible for
their own thread locals, child threads, I/O libraries and external resources.

## Measurements

```java
var profiler = getServer().getPerformanceManager();
profiler.configure(new PerformanceOptions(true, Duration.ofMillis(10), 100));
var plugin = profiler.getPluginSnapshot(); // own generation, server thread
var memory = profiler.getRuntimeMemorySnapshot();
```

Profiling defaults off. Enabling records callback calls, exceptions, aggregate/max
wall time and slow counts for events, commands, packet listeners, managed tasks and
legacy tasks. Existing `JavaPlugin.getDiagnostics()` provides activation/deactivation
durations and listener/task/service counts. Slow warnings identify callback category,
event/command label and thread kind through the owning plugin logger; warnings can
themselves incur logging cost. Counters are cumulative and live snapshots approximate.

`nativeBridgeCalls/WallNanos` cover owner-attributable Java-to-native operations using
the existing guarded gameplay boundary, including advanced/network/ecosystem calls.
They measure native boundary bodies, which may include nested work: **not pure JNI
transition latency**, callback CPU use, bootstrap logging, or all JVM native calls.
Unknown expired owners cannot be attributed to a live plugin. Nested measurements
may overlap; do not add them to derive CPU load. No CPU profiler is advertised.

Memory includes process-wide heap, GC collection/time counters, loaded class count,
and weakly tracked created/closed/live/open plugin classloaders. It does not attribute
heap bytes to individual plugins or count every JVM classloader. GC is nondeterministic;
closing a loader or observing a cleared WeakReference is not proof of full leak freedom.
Existing plugin diagnostics provide the other resource counts.

## Benchmarks and SDK

The offline local harness uses 3 warmup rounds and 15 measured batch rounds. JSON
includes raw batch means, median/P95, JVM arguments, allocation estimates, supported
platform-thread CPU time, heap and GC counters. P99 is deliberately absent: these
samples cannot establish per-operation tail latency. No cached JMH is available;
this harness has no JMH isolation, reliable blackholes or production load model.

From `java/`, using an installed Gradle and Java 25:

```text
gradle --offline --no-daemon -PzernythJavaVendor=any build
gradle --offline --no-daemon -PzernythJavaVendor=any :runtime:benchmarkJava
gradle --offline --no-daemon -PzernythJavaVendor=any :runtime:benchmarkManaged
```

The explicit vendor override is only for the documented local alternative JDK;
omit it for Temurin. On Windows `scripts/benchmark-java.cmd` accepts existing
Gradle/JDK paths, plus an optional `managed` argument. `scripts/benchmark-native.cmd`
accepts existing ctest/JDK paths, plus optional `check` for the JNI checker.
Use `-PbenchmarkOutput=<repository-output-path>` to preserve distinct runs. Fix
the same JDK, heap (`-Xms256m -Xmx512m`), fixture and build mode for comparisons.
`benchmarkJava` compares the original three Java cases; `benchmarkManaged` adds
admission/cancellation, server-queue admission with a mock host, optional profiling,
and an isolated Java lifecycle. Mock queue measurements are not native scheduler latency.

`JavaPerformanceTest.ReproducibleProductionBridgeHarness` uses the production bridge
with native mocks and is opt-in: provide `ZERNYTH_BENCHMARK_JAR`, `ZERNYTH_PERF_OUTPUT`,
JVM library and the API/runtime/benchmark-support JARs in the system classpath. Build
the fixtures with `:runtime:benchmarkPluginJar :runtime:benchmarkSupportJar`. Its
synthetic `ping` is test-only and separate from production player/service operations.
It also measures native/Java event dispatch, packet dispatch, listener registration,
schedule/cancel, heartbeat and plugin disable/re-enable cleanup. Enable `-Xcheck:jni`
for correctness runs and identify its instrumentation cost in timing reports.

`JavaAsyncTest` uses `ZERNYTH_ASYNC_JAR` (`:runtime:asyncIntegrationJar`) to test the
actual bridge/scheduler, virtual I/O, CPU workers, main handoff, thread rejection and
profiling. These are mock integrations, never evidence of real BDS load behavior.

SDK artifact is `dev.zernyth:api:0.6.0`, usable with Gradle `compileOnly` or Maven
`provided`, generated by `:api:publishApiPublicationToSdkRepository`. Sources/Javadoc
remain included. The existing Hello plugin exposes `/zernythasync start|cancel|status`
with `zernyth.diagnostics`; it performs cancellable computation and logs the result
only after a server-thread handoff. It never changes the world automatically.

Classloader isolation and quotas are not a security sandbox. Arbitrary plugin code
can block, allocate memory, create threads or harm the embedded JVM/BDS process.

See [validation and measured limits](validation.md).
