"""Starts a Zernyth Engine server.

Without options, uses the bedrock_server folder in the current directory, downloads Bedrock Dedicated Server
there after confirmation if it is missing, finds an installed JDK 25 and starts the server.
"""

import argparse
from importlib import metadata
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

from . import get_version

EULA = "https://www.minecraft.net/eula"
JDK_HINT = "Install a JDK 25, for example: winget install EclipseAdoptium.Temurin.25.JDK"


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
    probe = subprocess.run([str(home / "bin/java.exe"), "-version"], capture_output=True,
                           text=True, timeout=15, check=True, env=env)
    if not re.search(r'version "25[.\"]', probe.stdout + probe.stderr):
        raise ValueError("JDK 25 is required")
    jvm = home / "bin/server/jvm.dll"
    if not jvm.is_file():
        raise ValueError("Missing JDK 25 JVM library")
    return jvm


def _java_candidates():
    if os.environ.get("JAVA_HOME"):
        yield Path(os.environ["JAVA_HOME"])
    java = shutil.which("java")
    if java:
        yield Path(java).resolve().parent.parent
    for root in {os.environ.get("ProgramFiles", r"C:\Program Files"), os.environ.get("ProgramW6432", r"C:\Program Files")}:
        for pattern in ("Eclipse Adoptium/jdk-25*", "Java/jdk-25*", "Microsoft/jdk-25*", "Amazon Corretto/jdk25*",
                        "Zulu/zulu-25*", "BellSoft/LibericaJDK-25*"):
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
    if os.name != "nt":
        parser.error("This release wheel is supported only on Windows x64; on Ubuntu use zernyth-install.sh")
    home = args.java_home.resolve() if args.java_home else find_java_home()
    if home is None:
        parser.error("No JDK 25 found. " + JDK_HINT)
    try:
        jvm = _java_runtime(home)
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        parser.error(f"{home}: {error}. {JDK_HINT}")
    print(f"Java: {home}")

    from endstone.cli.windows import WindowsBootstrap

    class ExistingServer(WindowsBootstrap):
        def _add_loopback_exemption(self):
            # The upstream launch method calls this hook; Zernyth changes no OS policy.
            pass

    server = args.server.resolve()
    backend = ExistingServer(server_folder=str(server), no_confirm=args.yes, remote=
                             "https://raw.githubusercontent.com/EndstoneMC/bedrock-server-data/v2", interactive=False)
    if not backend.executable_path.exists():
        if args.check:
            parser.error(f"No Bedrock Dedicated Server in {server}")
        question = (f"Bedrock Dedicated Server {backend.minecraft_version} is not in {server}. Download it from "
                    f"minecraft.net now? By downloading you accept the Minecraft EULA ({EULA}).")
        if not _confirm(question, args.yes):
            parser.error(f"Put Bedrock Dedicated Server into {server}, use --server, or run again with --yes")
        server.mkdir(parents=True, exist_ok=True)
        backend._download(server)

    from .bds import inspect
    result = inspect(server)
    print(json.dumps(result, indent=2))
    if result["binaryInspection"] != "PASS":
        parser.error(f"Zernyth {get_version()} needs Bedrock Dedicated Server {result['expectedVersion']}; "
                     f"found {result['fileVersion']}")
    package = Path(__file__).resolve().parent
    build = json.loads((package / "BUILD.json").read_text(encoding="utf-8"))
    jars = [package / "java" / f"{name}-{build['zernyth']}.jar" for name in ("api", "runtime")]
    if not all(p.is_file() for p in jars):
        parser.error("Incomplete Java runtime installation")
    if args.check:
        print("Check passed. Run zernyth without --check to start the server.")
        return
    os.environ.update(JAVA_HOME=str(home), ZERNYTH_JAVA_ENABLED="1",
                      ZERNYTH_JVM_LIBRARY=str(jvm),
                      ZERNYTH_JAVA_CLASSPATH=os.pathsep.join(map(str, jars)))
    backend._validate()
    raise SystemExit(backend._run())
