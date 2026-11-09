# Third-party attribution and distribution notes

Zernyth Engine is a modified fork of [Endstone](https://github.com/EndstoneMC/endstone),
based on its 0.11.15 source release. The original Apache-2.0 [LICENSE](LICENSE),
source copyright headers, contribution guide, and release history are retained.
Zernyth-specific changes include the Java framework, JNI adapters, SDK, and
associated build, test, and documentation infrastructure. No original root
`NOTICE` file was present in the inherited baseline.

## Java runtime dependencies

The locally available POM metadata declares:

| Dependency | Version | Declared license |
|---|---|---|
| Gson | 2.14.0 | Apache-2.0 |
| SnakeYAML | 2.4 | Apache-2.0 |

These are dependency declarations, not a complete redistribution inventory.
The current generated Java license directory contains POM metadata but no
standalone license or notice texts from these JARs. Before distributing compiled
runtime artifacts, assemble and review the applicable license texts, copyrights,
and any required notices. Do not treat a successful packaging command as legal
clearance. Native dependencies also require review of the cached Conan package
license inventory; this source-preparation step does not certify that inventory.

Build and test tools have their own license terms. See `conanfile.py`,
`java/runtime/build.gradle.kts`, and accompanying dependency materials for the
actual dependency definitions. No dependency binaries are committed here.

## Minecraft and BDS

Minecraft and Bedrock Dedicated Server are owned by their respective rights
holders. No proprietary BDS executable, world, player data, or JDK is included.
Use a separately obtained BDS installation. Apache-2.0 does not grant rights to
third-party products or trademarks.
