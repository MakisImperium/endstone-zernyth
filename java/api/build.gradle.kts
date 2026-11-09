plugins { `java-library`; `maven-publish` }
publishing { publications { create<MavenPublication>("api") { from(components["java"]) } } }
java { withJavadocJar() }
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
publishing.repositories.maven { name = "sdk"; url = rootProject.layout.buildDirectory.dir("sdk-repository").get().asFile.toURI() }
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all", "-quiet")
    (options as StandardJavadocDocletOptions).addBooleanOption("Werror", true)
    (options as StandardJavadocDocletOptions).tags("implSpec:a:Implementation Requirements:")
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addBooleanOption("notimestamp", true)
}
