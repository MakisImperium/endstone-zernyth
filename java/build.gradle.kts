plugins { base }

// Temurin is the standard development JDK. An explicit vendor override permits
// an already installed, compatible JDK 25 without downloading another runtime.
val zernythJavaVendor = providers.gradleProperty("zernythJavaVendor").orElse("temurin").get()
require(zernythJavaVendor in setOf("temurin", "any")) {
    "zernythJavaVendor must be 'temurin' (default) or 'any' (explicit local override)"
}
allprojects {
    group = "dev.zernyth"
    version = rootProject.file("../zernyth-version.txt").readText().trim()
}
subprojects {
    apply(plugin = "java-library")
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        if (zernythJavaVendor == "temurin") {
            toolchain.vendor.set(JvmVendorSpec.ADOPTIUM)
        }
        withSourcesJar()
    }
    tasks.withType<JavaCompile>().configureEach { options.release.set(25) }
    tasks.withType<Jar>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        manifest.attributes("Implementation-Version" to project.version.toString(), "Zernyth-Engine-Version" to project.version.toString())
    }
    val javaToolchain = extensions.getByType<JavaPluginExtension>().toolchain
    val toolchains = extensions.getByType<JavaToolchainService>()
    tasks.withType<JavaExec>().configureEach {
        javaLauncher.set(toolchains.launcherFor(javaToolchain))
    }
}
