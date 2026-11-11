"""Write UTF-8 GitHub Actions path and digest outputs using preinstalled Python."""

import argparse
import hashlib
import os
from pathlib import Path


def add_tool_paths(value, destination):
    directories = [Path(item) for item in value.split(os.pathsep) if item.strip()]
    for directory in directories:
        if not directory.is_dir():
            raise ValueError(f"Configured tool directory does not exist: {directory}")
    with destination.open("a", encoding="utf-8", newline="\n") as output:
        for directory in directories:
            output.write(str(directory) + "\n")


def write_wheel_digest(wheel, destination):
    with wheel.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    with destination.open("a", encoding="utf-8", newline="\n") as output:
        output.write("sha256=" + digest + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("tools", "digest"))
    parser.add_argument("--wheel", type=Path)
    args = parser.parse_args()
    if args.command == "tools":
        add_tool_paths(os.environ.get("ZERNYTH_TOOL_PATHS", ""), Path(os.environ["GITHUB_PATH"]))
    else:
        if args.wheel is None:
            parser.error("digest requires --wheel")
        write_wheel_digest(args.wheel, Path(os.environ["GITHUB_OUTPUT"]))


if __name__ == "__main__":
    main()
