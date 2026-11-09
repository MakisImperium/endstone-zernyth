plugins { `java-library` }
dependencies { compileOnly(project(":api")) }
tasks.processResources {
    inputs.property("zernythVersion",project.version.toString())
    filesMatching("plugin.yml") { expand("version" to project.version.toString()) }
}
