"""Offline formatting and working-copy protection for the explicit Zernyth-owned scope.

No source discovery, downloads or repository-wide formatting is performed. The
manifest is reviewed and versioned; additions must be explicitly authorized.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "config/zernyth-format-files.json"


def checked_path(name):
    candidate = ROOT / name
    path = candidate.resolve()
    if (not path.is_relative_to(ROOT) or not path.is_file()
            or candidate.is_symlink()
            or any(parent.is_symlink() for parent in candidate.parents if parent != ROOT)):
        raise ValueError(f"Unsafe or missing source: {name}")
    if name != path.relative_to(ROOT).as_posix():
        raise ValueError(f"Noncanonical source path: {name}")
    return path


def check_java_sources(paths, java_home):
    """Check imports/layout with JDK symbols after IntelliJ has formatted the explicit files."""
    home = java_home or os.environ.get("JAVA_HOME")
    if not home:
        raise ValueError("Set JAVA_HOME or --java-home to an installed JDK 25; nothing is downloaded")
    java = Path(home) / "bin" / ("java.exe" if os.name == "nt" else "java")
    runtime = ROOT / f"java/runtime/build/libs/runtime-{(ROOT / 'zernyth-version.txt').read_text().strip()}.jar"
    if not java.is_file() or not runtime.is_file():
        raise ValueError("An installed JDK and locally built runtime JAR are required for Java symbol checks")
    build = ROOT / "build"
    build.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="zernyth-style-", dir=build) as directory:
        file_list = Path(directory) / "sources.txt"
        file_list.write_text("\n".join(str(path) for path in paths) + "\n", encoding="utf-8")
        result = subprocess.run([str(java), "--class-path", str(runtime),
                                 str(ROOT / "scripts/java/dev/zernyth/tools/ZernythJavaImports.java"),
                                 str(file_list), "--check-layout"], cwd=ROOT, capture_output=True,
                                encoding="utf-8", errors="replace", check=True, timeout=120)
        import_errors = []
        for record in result.stdout.splitlines():
            name, encoded = record.split("\t", 1)
            path = Path(name).resolve()
            if path not in paths:
                raise ValueError("The Java checker returned a path outside its reviewed source list")
            normalized = base64.b64decode(encoded).decode("utf-8")
            current = path.read_text(encoding="utf-8")
            if normalized != current:
                import_errors.append(path.relative_to(ROOT).as_posix())
            if re.search(r"(?m)^\t", current):
                raise ValueError(f"Tab indentation: {path}")
            long_lines = [number for number, line in enumerate(current.splitlines(), 1) if len(line) > 110]
            if long_lines:
                raise ValueError(f"Lines longer than 110 columns in {path}: {long_lines}")
            if not re.search(r"(?m)^package [\w.]+;\n(?:\n|\Z)", current):
                raise ValueError(f"Missing blank line after package: {path}")
            if re.search(r"(?m)^import [^\n]+;\n(?!(?:\n|import ))", current):
                raise ValueError(f"Missing blank line after imports: {path}")
        if import_errors:
            raise ValueError(f"Normalize explicit imports/avoidable qualified types before formatting: {import_errors}")


def format_sources(executable, write, java_home=None, language="all"):
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    files = manifest["files"]
    baseline = subprocess.run(["git", "cat-file", "-e", f"{manifest['baseline']}^{{commit}}"], cwd=ROOT,
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if baseline.returncode != 0:
        raise ValueError("The reviewed baseline commit must exist locally; no automatic fetch is performed")
    selected = []
    for name in files:
        path = checked_path(name)
        # Originally tracked upstream files must never enter the formatter list.
        tracked = subprocess.run(["git", "cat-file", "-e", f"{manifest['baseline']}:{name}"], cwd=ROOT,
                                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if tracked.returncode == 0:
            raise ValueError(f"Refusing baseline-owned file: {name}")
        if language == "all" or (language == "java") == (path.suffix == ".java"):
            selected.append(path)
    java_paths = [path for path in selected if path.suffix == ".java"]
    if write and java_paths:
        raise ValueError("Java formatting uses IntelliJ and the scoped .editorconfig files. "
                         "Reformat the manifest's Java files in the IDE; use format --language cpp for C++ only.")
    for path in selected:
        if path.suffix == ".java":
            continue
        command = [executable, "--style=file", "--Werror"]
        command += ["-i"] if write else ["--dry-run"]
        subprocess.run([*command, str(path)], cwd=ROOT, check=True, timeout=30)
    if java_paths:
        check_java_sources(java_paths, java_home)
    print(f"PASS: {'formatted' if write else 'checked'} {len(selected)} explicit Zernyth sources")


def protect(snapshot):
    baseline = json.loads(Path(snapshot).read_text(encoding="utf-8"))
    changed = []
    for name, expected in baseline["trackedBefore"].items():
        path = checked_path(name)
        actual = hashlib.sha256(path.read_bytes()).hexdigest()
        if actual != expected:
            changed.append(name)
    if changed:
        raise ValueError(f"Protected working-copy files changed: {changed}")
    print(f"PASS: {len(baseline['trackedBefore'])} protected tracked files unchanged")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("format", "check", "protect"))
    parser.add_argument("--clang-format", default="clang-format")
    parser.add_argument("--java-home", help="Existing JDK 25 for compiler-backed Java checks")
    parser.add_argument("--language", choices=("all", "java", "cpp"), default="all")
    parser.add_argument("--snapshot", type=Path, help="Explicit local file-hash snapshot for protect")
    args = parser.parse_args()
    if args.command == "protect":
        if args.snapshot is None:
            parser.error("protect requires --snapshot pointing to a local file-hash snapshot")
        protect(args.snapshot)
    else:
        format_sources(args.clang_format, args.command == "format", args.java_home, args.language)


if __name__ == "__main__":
    main()
