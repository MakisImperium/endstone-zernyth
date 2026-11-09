"""Offline operator launcher. Default is inspection only; never calls install/update/prepare."""
import argparse
import json
import os
from pathlib import Path
import sys
from zernyth_bds import inspect


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--run", action="store_true", help="Operator-controlled start; does not establish network isolation")
    args = parser.parse_args()
    home = Path(__file__).resolve().parents[1]
    metadata = json.loads((home / "BUILD.json").read_text())
    if not metadata["pythonVersion"].startswith(f"{sys.version_info.major}.{sys.version_info.minor}."):
        raise ValueError("Use the Python ABI listed in BUILD.json")
    result = inspect(args.server)
    print(json.dumps(result, indent=2))
    if result["binaryInspection"] != "PASS":
        raise ValueError("BDS compatibility not established for this executable/platform")
    jdk = args.java_home.resolve()
    import subprocess
    probe = subprocess.run([str(jdk / "bin" / ("java.exe" if os.name == "nt" else "java")), "-version"], capture_output=True, text=True, timeout=15, check=True)
    import re
    if not re.search(r'version "25[.\"]', probe.stdout + probe.stderr):
        raise ValueError("Use a validated JDK 25")
    if not args.run:
        print("Inspection only. No server started. Operator must establish network policy before --run.")
        return
    sys.path.insert(0, str(home / "lib/python"))
    os.environ["JAVA_HOME"] = str(jdk)
    os.environ["ZERNYTH_JAVA_ENABLED"] = "1"
    os.environ["ZERNYTH_JVM_LIBRARY"] = str(jdk / ("bin/server/jvm.dll" if os.name == "nt" else "lib/server/libjvm.so"))
    os.environ["ZERNYTH_JAVA_CLASSPATH"] = os.pathsep.join(str(home / f"java/{module}-{metadata['zernyth']}.jar") for module in ("api", "runtime"))
    if os.name == "nt":
        from endstone.cli.windows import WindowsBootstrap as Bootstrap
    else:
        from endstone.cli.linux import LinuxBootstrap as Bootstrap
    backend = Bootstrap(server_folder=str(args.server.resolve()), no_confirm=False, remote="", interactive=False)
    backend._validate()
    # Intentionally reuse only Endstone's existing launch backend. No _install/_update/_prepare.
    raise SystemExit(backend._run())


if __name__ == "__main__":
    main()
