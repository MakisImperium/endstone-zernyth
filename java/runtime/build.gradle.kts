import java.time.Duration

plugins { `java-library` }

tasks.register("allTestJars") {
    dependsOn("integrationJar","failureIntegrationJar","advancedIntegrationJar","advancedPeerIntegrationJar",
        "networkIntegrationJar","networkPeerIntegrationJar","ecosystemTestJars","asyncIntegrationJar",
        "benchmarkSupportJar","benchmarkPluginJar")
}
tasks.register<Copy>("thirdPartyLicenses") {
    dependsOn(tasks.jar)
    into(layout.buildDirectory.dir("third-party-licenses"))
    configurations.runtimeClasspath.get().filter { it.name.startsWith("gson-") || it.name.startsWith("snakeyaml-") }.forEach { artifact ->
        from(zipTree(artifact)) {
            include("META-INF/LICENSE*","META-INF/NOTICE*","META-INF/maven/**/pom.xml")
            into(artifact.name.removeSuffix(".jar"))
        }
    }
}

tasks.register<Jar>("asyncIntegrationJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("async-integration")
    from(sourceSets.test.get().output) { include("dev/zernyth/fixture/AsyncPlugin*") }
    from("src/test/async-fixture-resources")
}

tasks.register<Jar>("benchmarkSupportJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("benchmark-support")
    from(sourceSets.test.get().output) { include("dev/zernyth/runtime/BenchmarkHarness*") }
}

tasks.register<Jar>("lifecycleBenchmarkJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("lifecycle-benchmark")
    from(sourceSets.test.get().output) { include("dev/zernyth/fixture/LifecycleBenchmarkPlugin*") }
    from("src/test/lifecycle-benchmark-resources")
}

tasks.register<JavaExec>("benchmarkManaged") {
    dependsOn("benchmarkPluginJar","lifecycleBenchmarkJar")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.zernyth.runtime.BenchmarkHarness")
    args(layout.buildDirectory.file("libs/runtime-${project.version}-benchmark-plugin.jar").get().asFile.absolutePath,
        layout.buildDirectory.file("libs/runtime-${project.version}-lifecycle-benchmark.jar").get().asFile.absolutePath)
    jvmArgs("-Xms256m", "-Xmx512m")
    systemProperty("zernyth.benchmark.output",providers.gradleProperty("benchmarkOutput").orElse(rootProject.layout.buildDirectory.file("managed-benchmark.json").get().asFile.absolutePath).get())
}
tasks.register<Jar>("benchmarkPluginJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("benchmark-plugin")
    from(sourceSets.test.get().output) { include("dev/zernyth/fixture/PerformancePlugin*") }
    from("src/test/performance-fixture-resources")
}
tasks.register<JavaExec>("benchmarkJava") {
    dependsOn("benchmarkPluginJar")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.zernyth.runtime.BenchmarkHarness")
    args(layout.buildDirectory.file("libs/runtime-${project.version}-benchmark-plugin.jar").get().asFile.absolutePath)
    jvmArgs("-Xms256m", "-Xmx512m")
    systemProperty("zernyth.benchmark.output", providers.gradleProperty("benchmarkOutput").orElse(rootProject.layout.buildDirectory.file("java-benchmark.json").get().asFile.absolutePath).get())
}
repositories { mavenCentral() }
dependencies {
    api(project(":api"))
    implementation("org.yaml:snakeyaml:2.4")
    implementation("com.google.code.gson:gson:2.14.0")
}

// Runtime-only parser dependencies are bundled; plugins depend only on the API.
tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(provider { configurations.runtimeClasspath.get().filter { it.name.startsWith("snakeyaml-") || it.name.startsWith("gson-") }.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class")
}
tasks.register<JavaExec>("selfTest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.zernyth.runtime.RuntimeTest")
    jvmArgs("-Xms64m", "-Xmx256m")
    timeout.set(Duration.ofMinutes(2))
    debugOptions { host.set("127.0.0.1"); port.set(5005); server.set(true); suspend.set(true) }
}

// The offline test suite is an executable harness, not a JUnit dependency.
tasks.test {
    dependsOn("selfTest")
    failOnNoDiscoveredTests.set(false)
}
tasks.register<Jar>("integrationJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("integration")
    from(sourceSets.test.get().output) { include("dev/zernyth/fixture/**") }
    from("src/test/fixture-resources")
}
tasks.register<Jar>("failureIntegrationJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("failure-integration")
    from(sourceSets.test.get().output) { include("dev/zernyth/fixture/**") }
    from("src/test/failure-fixture-resources")
}
tasks.register<Jar>("advancedIntegrationJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("advanced-integration")
    from(sourceSets.test.get().output) { include("dev/zernyth/fixture/**") }
    from("src/test/advanced-fixture-resources")
}
tasks.register<Jar>("advancedPeerIntegrationJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("advanced-peer-integration")
    from(sourceSets.test.get().output) { include("dev/zernyth/fixture/**") }
    from("src/test/advanced-peer-fixture-resources")
}

tasks.register<Jar>("networkIntegrationJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("network-integration")
    from(sourceSets.test.get().output) { include("dev/zernyth/fixture/**") }
    from("src/test/network-fixture-resources")
}

tasks.register<Jar>("networkPeerIntegrationJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("network-peer-integration")
    from(sourceSets.test.get().output) { include("dev/zernyth/fixture/**") }
    from("src/test/network-peer-fixture-resources")
}

tasks.register<Jar>("ecosystemProviderJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("ecosystem-provider")
    from(sourceSets.test.get().output) { include("dev/zernyth/fixture/ecosystem/ProviderPlugin*"); include("dev/zernyth/fixture/shared/**") }
    from("src/test/ecosystem-provider")
}

tasks.register<Jar>("ecosystemConsumerJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("ecosystem-consumer")
    from(sourceSets.test.get().output) { include("dev/zernyth/fixture/ecosystem/ConsumerPlugin*") }
    from("src/test/ecosystem-consumer")
}

tasks.register<Jar>("ecosystemHardJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("ecosystem-hard")
    from(sourceSets.test.get().output) { include("dev/zernyth/fixture/ecosystem/ConsumerPlugin*") }
    from("src/test/ecosystem-hard")
}

tasks.register<Jar>("ecosystemEarlyJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("ecosystem-early")
    from(sourceSets.test.get().output) { include("dev/zernyth/fixture/ecosystem/ProviderPlugin*"); include("dev/zernyth/fixture/shared/**") }
    from("src/test/ecosystem-early")
}

tasks.register<Jar>("ecosystemCycleaJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("ecosystem-cyclea")
    from(sourceSets.test.get().output) { include("dev/zernyth/fixture/ecosystem/ProviderPlugin*"); include("dev/zernyth/fixture/shared/**") }
    from("src/test/ecosystem-cyclea")
}

tasks.register<Jar>("ecosystemCyclebJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("ecosystem-cycleb")
    from(sourceSets.test.get().output) { include("dev/zernyth/fixture/ecosystem/ProviderPlugin*"); include("dev/zernyth/fixture/shared/**") }
    from("src/test/ecosystem-cycleb")
}

tasks.register<Jar>("ecosystemMissingJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("ecosystem-missing")
    from(sourceSets.test.get().output) { include("dev/zernyth/fixture/ecosystem/ProviderPlugin*"); include("dev/zernyth/fixture/shared/**") }
    from("src/test/ecosystem-missing")
}

tasks.register<Jar>("ecosystemBadloadJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("ecosystem-badload")
    from(sourceSets.test.get().output) { include("dev/zernyth/fixture/ecosystem/ProviderPlugin*"); include("dev/zernyth/fixture/shared/**") }
    from("src/test/ecosystem-badload")
}

tasks.register<Jar>("ecosystemBadenableJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("ecosystem-badenable")
    from(sourceSets.test.get().output) { include("dev/zernyth/fixture/ecosystem/ProviderPlugin*"); include("dev/zernyth/fixture/shared/**") }
    from("src/test/ecosystem-badenable")
}

tasks.register("ecosystemTestJars") {
    dependsOn("ecosystemProviderJar", "ecosystemConsumerJar", "ecosystemHardJar", "ecosystemEarlyJar", "ecosystemCycleaJar", "ecosystemCyclebJar", "ecosystemMissingJar", "ecosystemBadloadJar", "ecosystemBadenableJar")
}
