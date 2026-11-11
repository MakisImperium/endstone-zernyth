# Alpha 1 release finalization

Zernyth Engine **0.9.0a1**, native/Java version **0.9.0-alpha.1**.
The local and GitHub packaging and installation gates pass. The successful Windows
CI run uses Eclipse Temurin 25.0.5. Package uploads remain owner-controlled manual steps.

## Executed validation

| Check | Status | Scope |
|---|---|---|
| Java 25 build and executable self-tests | PASS | Oracle local validation plus Eclipse Temurin 25.0.5 in CI |
| Windows C++ and Python extension rebuild | PASS | Clang 23.1.4, MSVC 14.51, CMake 4.3.1 |
| Native/JNI regression tests | PASS | 249/249, nine JNI-checker processes, zero reported violations |
| Archived plugin compatibility | PASS | Seven 0.2–0.8 JARs through the production bridge and native mocks |
| Python tests | PASS | 46 tests in CI |
| PEP 517 wheel construction | PASS | pip frontend, explicit matching native build |
| Core Metadata and CommonMark rendering | PASS | packaging.metadata and installed markdown-it-py |
| Wheel contents and RECORD hashes | PASS | 152 members, x64 PE and Java 25 class-header checks |
| Native path sanitization | PASS | Mandatory --require-sanitized, zero reported profile paths |
| License collection | PASS | Full original notices, hashes, Windows linkage gate |
| Fresh venv installation and pip check | PASS | All dependencies installed offline from existing cached wheels |
| Native imports, installed CLI and Java 25 detection | PASS | Isolated interpreter outside the source directory |
| Equal-input wheel reproduction | PASS | Two package builds using the same compiled inputs |
| GitHub build execution | PASS | [Run 43105562164](https://github.com/MakisImperium/endstone-zernyth/actions/runs/43105562164), source commit bd5ff94559ee233ac44bb9c00a686633dfba8b98 |
| Private PyPI/TestPyPI publisher settings | NOT TESTED | Owner account confirmation required |
| Temurin 25.0.5 Windows JNI runtime | PASS | Nine checker processes, 249 native/JNI tests |
| Linux and BDS/client execution | NOT TESTED | Separate platform and user-operated runtime checks |

The local compiler emitted existing getenv deprecation diagnostics. Cpptrace uses
DbgHelp on Windows. The mandatory linker-map check finds no linked libdwarf object
or exported DWARF API. The Windows distribution retains the graph's license texts
conservatively. Linux needs its own linkage and license assessment.

## Verified GitHub wheel

`zernyth_engine-0.9.0a1-cp315-cp315-win_amd64.whl` contains **4,902,653 bytes**.

```text
SHA256 2d521e3754c5a695dfd9a381b72f46027b39232ace7cc5cc69aa6376d67e9df9
```

The downloaded CI wheel and its `.whl.sha256` sidecar are in
`build/publish-018/github-43105562164`. The archive digest matches GitHub metadata,
and the wheel digest matches the validated CI output. Strict path/privacy checks
pass again after downloading the artifact.
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

Runner HOME is online with the Windows x64 and `zernyth-publish` labels. The
Conan, Gradle, dependency wheelhouse, tool PATH and explicit Python variables are
configured. The build-only workflow completed successfully on this runner.

The Windows steps use cmd and Python. A job-scoped canonical Git path resolves
checkout ownership handling, while UTF-8 file commands configure tools and transfer
the validated wheel digest between jobs.

The authenticated repository inspection confirmed the testpypi and pypi environments
exist. Both currently have no reviewer rules. The owner chooses their protection
policy and confirms the separate Trusted Publishers in the two index accounts.
Use owner `MakisImperium`, repository `endstone-zernyth`, workflow `publish.yml`,
project `zernyth-engine`, and the matching environment name.

The owner can now confirm the Trusted Publisher and explicitly select testpypi. PyPI uses
a separate target and requires the user-created `v0.9.0-alpha.1` tag at the reviewed
commit. The [publishing guide](pypi.md) records the complete procedure.
