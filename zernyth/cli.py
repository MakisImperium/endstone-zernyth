"""Starts a Zernyth Engine server.

Without options, uses the bedrock_server folder in the current directory, downloads Bedrock Dedicated Server
there after confirmation if it is missing, finds an installed JDK 25 and starts the server.
"""

import argparse
import hashlib
from importlib import metadata
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys

from . import get_version

EULA = "https://www.minecraft.net/eula"
REMOTE = "https://raw.githubusercontent.com/EndstoneMC/bedrock-server-data/v2"
WINDOWS = os.name == "nt"
JDK_HINT = ("Install a JDK 25, for example: winget install EclipseAdoptium.Temurin.25.JDK" if WINDOWS else
            "Install a JDK 25, for example Eclipse Temurin 25: https://adoptium.net/installation/linux/")


def reject_conflicting_distribution():
    """Refuse a shared environment whose endstone files may have been overwritten."""
    try:
        metadata.distribution("endstone")
    except metadata.PackageNotFoundError:
        return
    raise RuntimeError("endstone and zernyth-engine share files. Install Zernyth in a separate fresh venv.")


def _java_runtime(home):
    """Check an installed JDK 25 and its JVM library without starting BDS."""
    home = Path(home).resolve()
    env = os.environ.copy()
    for key in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"):
        env.pop(key, None)
    java = home / ("bin/java.exe" if WINDOWS else "bin/java")
    probe = subprocess.run([str(java), "-version"], capture_output=True, text=True, timeout=15, check=True, env=env)
    if not re.search(r'version "25[.\"]', probe.stdout + probe.stderr):
        raise ValueError("JDK 25 is required")
    jvm = home / ("bin/server/jvm.dll" if WINDOWS else "lib/server/libjvm.so")
    if not jvm.is_file():
        raise ValueError("Missing JDK 25 JVM library")
    return jvm


def _java_candidates():
    if os.environ.get("JAVA_HOME"):
        yield Path(os.environ["JAVA_HOME"])
    java = shutil.which("java")
    if java:
        yield Path(java).resolve().parent.parent
    if WINDOWS:
        roots = {os.environ.get("ProgramFiles", r"C:\Program Files"), os.environ.get("ProgramW6432", r"C:\Program Files")}
        patterns = ("Eclipse Adoptium/jdk-25*", "Java/jdk-25*", "Microsoft/jdk-25*", "Amazon Corretto/jdk25*",
                    "Zulu/zulu-25*", "BellSoft/LibericaJDK-25*")
    else:
        roots, patterns = {"/usr/lib/jvm", "/opt"}, ("*25*", "java/*25*")
    for root in roots:
        for pattern in patterns:
            yield from sorted(Path(root).glob(pattern), reverse=True)


def find_java_home():
    """Return the first installed JDK 25, or None."""
    for home in _java_candidates():
        try:
            _java_runtime(home)
            return home.resolve()
        except (OSError, ValueError, subprocess.SubprocessError):
            continue
    return None


def _confirm(question, assume_yes):
    if assume_yes:
        return True
    if not sys.stdin.isatty():
        return False
    return input(question + " [Y/n] ").strip().lower() in ("", "y", "yes", "j", "ja")


def _bootstrap_class():
    if WINDOWS:
        from endstone.cli.windows import WindowsBootstrap

        class Server(WindowsBootstrap):
            def _add_loopback_exemption(self):
                # The upstream launch method calls this hook; Zernyth changes no OS policy.
                pass

        return Server

    from endstone.cli.linux import LinuxBootstrap

    class Server(LinuxBootstrap):
        @property
        def _endstone_runtime_env(self):
            env = super()._endstone_runtime_env
            # The validated Linux start uses the system allocator, not the bundled mimalloc.
            env["LD_PRELOAD"] = "libc.so.6:" + str(self._endstone_runtime_path)
            return env

    return Server


def check_bds(server, build):
    """Windows reads the file version; Linux compares the validated executable hash."""
    if WINDOWS:
        from .bds import inspect
        result = inspect(server)
        return result["binaryInspection"] == "PASS", result
    executable = Path(server) / "bedrock_server"
    with executable.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    expected = build.get("bdsSha256", "")
    return digest == expected, {"expectedVersion": build.get("bds"), "sha256": digest, "expectedSha256": expected}


def main(argv=None):
    parser = argparse.ArgumentParser(prog="zernyth", description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--version", action="version", version="Zernyth Engine " + get_version())
    parser.add_argument("--server", type=Path, default=Path("bedrock_server"),
                        help="BDS folder (default: ./bedrock_server)")
    parser.add_argument("--java-home", type=Path, help="JDK 25 folder (default: found automatically)")
    parser.add_argument("--check", action="store_true", help="Only check BDS and Java; do not start the server")
    parser.add_argument("-y", "--yes", action="store_true", help="Answer yes to all questions")
    parser.add_argument("--run", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args(argv)
    reject_conflicting_distribution()
    if not (WINDOWS or (sys.platform == "linux" and platform.machine() == "x86_64")):
        parser.error("Zernyth supports Windows x64 and Linux x86_64")
    home = args.java_home.resolve() if args.java_home else find_java_home()
    if home is None:
        parser.error("No JDK 25 found. " + JDK_HINT)
    try:
        jvm = _java_runtime(home)
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        parser.error(f"{home}: {error}. {JDK_HINT}")
    print(f"Java: {home}")

    server = args.server.resolve()
    backend = _bootstrap_class()(server_folder=str(server), no_confirm=args.yes, remote=REMOTE, interactive=False)
    if not backend.executable_path.exists():
        if args.check:
            parser.error(f"No Bedrock Dedicated Server in {server}")
        question = (f"Bedrock Dedicated Server {backend.minecraft_version} is not in {server}. Download it from "
                    f"minecraft.net now? By downloading you accept the Minecraft EULA ({EULA}).")
        if not _confirm(question, args.yes):
            parser.error(f"Put Bedrock Dedicated Server into {server}, use --server, or run again with --yes")
        server.mkdir(parents=True, exist_ok=True)
        backend._download(server)

    package = Path(__file__).resolve().parent
    build = json.loads((package / "BUILD.json").read_text(encoding="utf-8"))
    compatible, result = check_bds(server, build)
    print(json.dumps(result, indent=2))
    if not compatible:
        parser.error(f"Zernyth {get_version()} needs Bedrock Dedicated Server {result['expectedVersion']}; "
                     f"the server in {server} is a different build")
    jars = [package / "java" / f"{name}-{build['zernyth']}.jar" for name in ("api", "runtime")]
    if not all(p.is_file() for p in jars):
        parser.error("Incomplete Java runtime installation")
    if args.check:
        print("Check passed. Run zernyth without --check to start the server.")
        return
    os.environ.update(JAVA_HOME=str(home), ZERNYTH_JAVA_ENABLED="1",
                      ZERNYTH_JVM_LIBRARY=str(jvm),
                      ZERNYTH_JAVA_CLASSPATH=os.pathsep.join(map(str, jars)))
    server.joinpath("plugins").mkdir(exist_ok=True)
    backend._validate()
    raise SystemExit(backend._run())
