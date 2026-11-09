# Validation and compatibility

This is a public summary of previously recorded local checks, not a new test run
or a production-readiness declaration. Engine version: **0.9.0-rc.1**. Native
foundation: **Endstone 0.11.15**. BDS contract baseline: **1.26.60.6**; other BDS
versions are not approved by this candidate.

| Gate | Status | Scope |
|---|---|---|
| Java and Windows native/Python build | PASS | Cached local toolchain; Oracle JDK 25 |
| Native/JNI regression suite | PASS | 249 tests in the latest recorded style-validation run |
| JNI checker | PASS | Nine integration processes; no reported checker violations |
| Python tooling tests | PASS | 21 release, quality, and comparison tests |
| Archived example JARs | PASS | 0.2–0.8 lifecycle regressions with native mocks |
| API compatibility | PASS | Public descriptors checked across 110 API classes |
| Javadocs and API compiler lint | PASS | Doclint/lint with warnings as errors |
| Local packaging | PASS | Earlier candidate archive; not rebuilt for later source edits |
| Real BDS/client gameplay for this candidate | NOT TESTED | Requires verified isolation and a compatible client |
| BDS smoke-test preparation | BLOCKED | Network isolation not demonstrated |
| Linux and Temurin 25 | NOT TESTED | No corresponding completed platform run |
| Full-core sanitizers and long-running load | NOT TESTED | Helper-only results do not cover the complete core |
| Manual CI | NOT TESTED | Prepared workflow has not been triggered |
| Binary redistribution license inventory | BLOCKED | Java inventory currently contains POMs without standalone license/notice texts; native inventory review remains open |

Recorded Windows environment: Oracle JDK 25.0.5.0.3, Clang 23.1.4 with MSVC 14.51
headers, CMake 4.3.1, Gradle 9.8.2, and Python 3.15.2. These results do not establish
Temurin compatibility, Linux support, maximum player capacity, TPS guarantees, or
comparative superiority over other frameworks.

The Java API requires Java 25 without preview features. Gameplay operations obey
native thread-affinity rules. Networking remains read-only metadata. Bukkit/Paper
compatibility is limited to documented porting support, and plugins are not
isolated processes. A passed JNI checker is not a proof of memory safety.

Before distributing an alpha, rebuild matching native/Java artifacts, verify the
archive and licenses, repeat applicable regressions, and explicitly record all
remaining gates. See the [release guide](zernyth-release-0.9.md).
