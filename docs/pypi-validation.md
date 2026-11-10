# Alpha 1 release finalization

Zernyth Engine **0.9.0a1**, native/Java version **0.9.0-alpha.1**.
The local packaging and installation gates pass. GitHub execution requires a
provisioned Windows runner. Package uploads remain owner-controlled manual steps.

## Executed validation

| Check | Status | Scope |
|---|---|---|
| Java 25 build and executable self-tests | PASS | Offline Gradle 9.8.2, Oracle JDK 25.0.5.0.3 |
| Windows C++ and Python extension rebuild | PASS | Clang 23.1.4, MSVC 14.51, CMake 4.3.1 |
| Native/JNI regression tests | PASS | 249/249, nine JNI-checker processes, zero reported violations |
| Archived plugin compatibility | PASS | Seven 0.2–0.8 JARs through the production bridge and native mocks |
| Python tests | PASS | 42 tests |
| PEP 517 wheel construction | PASS | pip frontend, explicit matching native build |
| Core Metadata and CommonMark rendering | PASS | packaging.metadata and installed markdown-it-py |
| Wheel contents and RECORD hashes | PASS | 152 members, x64 PE and Java 25 class-header checks |
| Native path sanitization | PASS | Mandatory --require-sanitized, zero reported profile paths |
| License collection | PASS | Full original notices, hashes, Windows linkage gate |
| Fresh venv installation and pip check | PASS | All dependencies installed offline from existing cached wheels |
| Native imports, installed CLI and Java 25 detection | PASS | Isolated interpreter outside the source directory |
| Equal-input wheel reproduction | PASS | Two package builds using the same compiled inputs |
| GitHub build execution | BLOCKED | Last API inspection found zero registered self-hosted runners |
| Private PyPI/TestPyPI publisher settings | NOT TESTED | Owner account confirmation required |
| Temurin runtime, Linux and BDS/client execution | NOT TESTED | Separate platform and user-operated runtime checks |

The local compiler emitted existing getenv deprecation diagnostics. Cpptrace uses
DbgHelp on Windows. The mandatory linker-map check finds no linked libdwarf object
or exported DWARF API. The Windows distribution retains the graph's license texts
conservatively. Linux needs its own linkage and license assessment.

## Final local wheel

`zernyth_engine-0.9.0a1-cp315-cp315-win_amd64.whl` contains **4,902,577 bytes**.

```text
SHA256 23be3c39d4597559c13549ce3fca3311bd3eab68aae594d20ab97726c79455f5
```

The wheel and its `.whl.sha256` sidecar are in `build/publish-018/dist`.
Local logs, cached dependencies and temporary test environments stay outside Git.

## Implemented corrections

- Compiler file/debug-prefix mapping and basename-only PDB references remove
  native personal paths during compilation/linking.
- Sentry/Crashpad, Cpptrace and the existing RakNet dependency were rebuilt from
  cached sources using `config/conan/windows-sanitized.profile`.
- The release driver refreshes the resolved Sentry binary directory explicitly,
  correcting CMake's retained cache path after a dependency revision changes.
- Nineteen original Sentry/Crashpad nested license and copyright texts supplement
  the 29 native-package license directories, Java notices and complete Apache
  texts, Endstone attribution, and JetBrains Mono's original SIL OFL.
- Strict Core Metadata and CommonMark rendering use installed dependencies.
  The workflow's extra Twine requirement was removed.
- Installed CLI validation now exercises actual Java 25 discovery without BDS.
- SHA-256 job outputs verify the wheel after transfer to each OIDC publishing job.
- Upload targets remain explicit manual choices, defaulting to build-only. GitHub
  applies the owner's configured environment protection rules. Independent reviewer
  requirements are an owner policy choice.
- AGENTS.md and CLAUDE.md remain tracked according to the owner's latest decision.

## Installation

Use a fresh CPython 3.15 x64 virtual environment. Zernyth replaces the official
Endstone distribution while retaining its import namespace. Python plugins with
an explicit pip dependency on the `endstone` distribution need adjusted dependency
metadata for this fork.

```powershell
py -3.15 -m venv .venv
.venv\Scripts\python -m pip install PATH_TO_WHEEL\zernyth_engine-0.9.0a1-cp315-cp315-win_amd64.whl
.venv\Scripts\python -m pip check
.venv\Scripts\zernyth --version
.venv\Scripts\zernyth --help
```

The complete offline install test additionally supplies the local dependency
wheelhouse with `--no-index --find-links`. Java 25 and BDS 1.26.60.6 are external
requirements. The operator controls BDS installation, configuration and runtime.

## Remaining external steps

Register a provisioned trusted Windows x64 runner with label `zernyth-publish`,
CPython 3.15, the supported toolchain, sanitized offline Conan packages and Gradle
caches. Configure `ZERNYTH_CONAN_HOME`, `ZERNYTH_GRADLE` and `ZERNYTH_WHEELHOUSE`.
Then execute `publish.yml` with the reviewed full commit SHA and target build-only.

The authenticated repository inspection confirmed the testpypi and pypi environments
exist. Both currently have no reviewer rules. The owner chooses their protection
policy and confirms the separate Trusted Publishers in the two index accounts.
Use owner `MakisImperium`, repository `endstone-zernyth`, workflow `publish.yml`,
project `zernyth-engine`, and the matching environment name.

After the GitHub build passes, the owner can explicitly select testpypi. PyPI uses
a separate target and requires the user-created `v0.9.0-alpha.1` tag at the reviewed
commit. The [publishing guide](pypi.md) records the complete procedure.
