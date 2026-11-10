# Zernyth Engine 0.9.0 Alpha 1

A Java 25 plugin framework integrated into Endstone and the original Minecraft
Bedrock Dedicated Server. This is an experimental alpha, not a standalone game
server or a declaration of production readiness.

## Installation

The first wheel targets **Windows x64 and CPython 3.15**. Use a new virtual
environment. Zernyth replaces the Endstone distribution while retaining the
`endstone` import namespace; **do not install both distributions together**.

```powershell
py -3.15 -m venv .venv
.venv\Scripts\python -m pip install zernyth-engine==0.9.0a1
.venv\Scripts\zernyth --version
```

The command above applies after an approved PyPI upload; this project is currently
preparing that upload. Until then, install the reviewed local Windows wheel.

## External requirements

- An existing compatible BDS **1.26.60.6** directory, obtained separately.
- An installed **JDK 25**, without preview features. Eclipse Temurin is preferred.
- Microsoft Visual C++ runtime libraries required by the native Windows build.
- Operator-managed networking and a separate test world; signaling on localhost
  alone does not prove the server is isolated.

Inspect an existing installation without starting it:

```powershell
zernyth --server "D:\BDS-Test" --java-home "D:\JDK25"
```

Only an explicit `--run` starts the server. The launcher reuses Endstone's native
Windows bootstrap without its OS loopback-policy modification. It does not
download BDS, install Java, prepare a world, or change firewall settings. Prepare
server configuration and plugin directories separately before starting it.

## Included components and limits

The wheel includes the Python compatibility modules, native runtime and JNI
integration, Java API/runtime JARs, defaults, and dependency license materials.
BDS binaries, the JDK, examples installed as active plugins, and test worlds are
not included. One process embeds one JVM. Java plugins are trusted code, not
isolated sandbox processes. Networking exposes read-only packet metadata.

Windows builds, native/JNI mocks, and isolated wheel installation have been tested.
They do not establish real-client gameplay compatibility. Linux wheels, Temurin
runtime validation, real BDS execution of this alpha, and hosted publishing remain
separate open checks.

Source, installation details, and current limitations:
[Zernyth Engine](https://github.com/MakisImperium/endstone-zernyth) ·
[Packaging guide](https://github.com/MakisImperium/endstone-zernyth/blob/main/docs/pypi.md).

Zernyth is a modified Endstone fork under Apache-2.0. Original notices are
retained; dependencies have their own accompanying license materials. Minecraft
and BDS are separate products and are not redistributed in this wheel.
