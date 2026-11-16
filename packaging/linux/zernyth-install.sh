#!/usr/bin/env bash
# Zernyth Engine installer for Ubuntu 24.04 x86_64.
#   zernyth-install.sh install [VERSION]     install the latest stable release (or VERSION)
#   zernyth-install.sh update                install a newer release next to the current one and switch to it
#   zernyth-install.sh rollback              switch back to the previous version
#   zernyth-install.sh start [--server DIR] [--java-home DIR] [--run]
#   zernyth-install.sh status | versions | prune
# Each version lives in its own folder; "current" points to the active one. BDS, worlds and plugins are not touched.
set -euo pipefail

REPO=MakisImperium/endstone-zernyth
HOME_DIR=${ZERNYTH_HOME:-$HOME/zernyth}
API=${ZERNYTH_API:-https://api.github.com/repos/$REPO}
VERSIONS=$HOME_DIR/versions
CONFIG=$HOME_DIR/config
WORK=""
trap '[[ -n $WORK ]] && rm -rf "$WORK"' EXIT

die() { echo "zernyth-install: $*" >&2; exit 1; }
say() { echo "==> $*"; }

need() {
    local cmd
    for cmd in curl tar sha256sum python3; do
        command -v "$cmd" >/dev/null 2>&1 || die "missing command: $cmd (sudo apt install $cmd)"
    done
    [[ $(uname -m) == x86_64 ]] || die "only x86_64 is supported"
    if [[ -r /etc/os-release ]] && ! grep -q '^VERSION_ID="24.04"' /etc/os-release; then
        echo "Warning: Zernyth is built and tested for Ubuntu 24.04." >&2
    fi
}

# Prints "<tag> <archive name> <archive url> <checksums url>" for a release ("latest" or a version).
release_info() {
    local which=$1 url json
    if [[ $which == latest ]]; then url=$API/releases/latest; else url=$API/releases/tags/v${which#v}; fi
    json=$(curl -fsSL -H "Accept: application/vnd.github+json" "$url") || die "release $which not found on GitHub"
    printf '%s' "$json" | python3 -c '
import json, re, sys
data = json.load(sys.stdin)
tag = data["tag_name"]
assets = {a["name"]: a["browser_download_url"] for a in data.get("assets", [])}
pattern = re.compile(r"zernyth-engine-" + re.escape(tag.lstrip("v")) + r"-ubuntu24-x86_64-cp\d+\.tar\.gz")
archive = next((n for n in sorted(assets) if pattern.fullmatch(n)), None)
if data.get("draft") or data.get("prerelease"):
    sys.exit("release " + tag + " is not a stable release")
if not archive or "SHA256SUMS" not in assets:
    sys.exit("release " + tag + " has no Ubuntu archive or SHA256SUMS")
print(tag, archive, assets[archive], assets["SHA256SUMS"])
' | tr -d '\r' || die "could not read release information for $which"
}

active_version() { [[ -L $HOME_DIR/current ]] && basename "$(dirname "$(readlink "$HOME_DIR/current")")" || true; }

# Points a link at a target in one step.
relink() {
    ln -sfn "$2" "$1.new"
    mv -Tf "$1.new" "$1"
}

install_version() {
    local which=$1 tag archive archive_url sums_url version dir root expected bad listing
    need
    read -r tag archive archive_url sums_url < <(release_info "$which")
    version=${tag#v}
    dir=$VERSIONS/$version
    if [[ -f $dir/.installed ]]; then
        say "Zernyth $version is already installed."
    else
        mkdir -p "$VERSIONS"
        WORK=$(mktemp -d "$HOME_DIR/.download.XXXXXX")
        say "Downloading Zernyth $version"
        curl -fL --retry 3 -o "$WORK/$archive" "$archive_url"
        curl -fsSL --retry 3 -o "$WORK/SHA256SUMS" "$sums_url"
        expected=$(awk -v f="$archive" '$2 == f || $2 == "*" f { print $1 }' "$WORK/SHA256SUMS")
        [[ $expected =~ ^[0-9a-f]{64}$ ]] || die "SHA256SUMS has no entry for $archive"
        echo "$expected  $WORK/$archive" | sha256sum --check --status || die "checksum mismatch for $archive; nothing was installed"
        say "Checksum OK"
        root=${archive%.tar.gz}
        listing=$(tar -tzf "$WORK/$archive") || die "$archive cannot be read; nothing was installed"
        bad=$(printf '%s\n' "$listing" | awk -v r="$root" '$0 != r && index($0, r "/") != 1 || $0 ~ /(^|\/)\.\.(\/|$)/' | head -n1 || true)
        [[ -z $bad ]] || die "unexpected path in $archive: $bad"
        rm -rf "$dir"
        mkdir -p "$dir"
        tar -xzf "$WORK/$archive" -C "$dir" --no-same-owner
        say "Installing (offline, from the archive)"
        if ! (cd "$dir/$root" && ./install.sh); then
            rm -rf "$dir"
            die "installation of $version failed; the active version is unchanged"
        fi
        echo "$root" > "$dir/.installed"
    fi
    root=$(cat "$dir/.installed")
    local previous
    previous=$(active_version)
    if [[ $previous != "$version" ]]; then
        [[ -n $previous ]] && relink "$HOME_DIR/previous" "$(readlink "$HOME_DIR/current")"
        relink "$HOME_DIR/current" "$dir/$root"
    fi
    if [[ -f $0 && $(basename "$0") == zernyth-install* && $0 -ef $HOME_DIR/zernyth-install ]]; then :
    elif [[ -f $0 && $(basename "$0") == zernyth-install* ]]; then
        cp -f "$0" "$HOME_DIR/zernyth-install" && chmod +x "$HOME_DIR/zernyth-install"
    fi
    say "Zernyth $version is active${previous:+ (previous: $previous)}."
    echo "Start it with: $HOME_DIR/zernyth-install start --server /path/to/bds --java-home /path/to/jdk25"
    [[ -n $previous && $previous != "$version" ]] && echo "Restart a running server to use the new version."
    return 0
}

cmd_update() {
    local current tag latest
    need
    current=$(active_version)
    [[ -n $current ]] || die "Zernyth is not installed yet; run: $0 install"
    read -r tag _ < <(release_info latest)
    latest=${tag#v}
    if [[ $latest == "$current" ]]; then say "Zernyth $current is up to date."; return 0; fi
    if [[ $(printf '%s\n%s\n' "$current" "$latest" | sort -V | tail -n1) != "$latest" ]]; then
        say "The active version $current is newer than the latest stable release $latest; nothing to do."
        return 0
    fi
    install_version "$latest"
}

cmd_rollback() {
    [[ -L $HOME_DIR/previous ]] || die "there is no previous version to go back to"
    local current
    current=$(readlink "$HOME_DIR/current")
    relink "$HOME_DIR/current" "$(readlink "$HOME_DIR/previous")"
    relink "$HOME_DIR/previous" "$current"
    say "Zernyth $(active_version) is active again. Restart a running server to use it."
}

cmd_start() {
    [[ -L $HOME_DIR/current ]] || die "Zernyth is not installed yet; run: $0 install"
    local server="" java="" run=() arg
    [[ -f $CONFIG ]] && { server=$(sed -n 's/^server=//p' "$CONFIG"); java=$(sed -n 's/^java_home=//p' "$CONFIG"); }
    while (($#)); do
        case $1 in
            --server) server=$2; shift 2 ;;
            --java-home) java=$2; shift 2 ;;
            --run) run=(--run); shift ;;
            *) die "unknown option: $1" ;;
        esac
    done
    [[ -n $server && -n $java ]] || die "pass --server /path/to/bds and --java-home /path/to/jdk25 once; they are remembered"
    server=$(cd "$server" && pwd) || die "BDS folder not found"
    java=$(cd "$java" && pwd) || die "JDK folder not found"
    mkdir -p "$HOME_DIR"
    printf 'server=%s\njava_home=%s\n' "$server" "$java" > "$CONFIG"
    exec "$HOME_DIR/current/start.sh" --server "$server" --java-home "$java" "${run[@]}"
}

cmd_status() {
    local current previous
    current=$(active_version)
    [[ -n $current ]] || { echo "Zernyth is not installed (looked in $HOME_DIR)."; return 0; }
    previous=$([[ -L $HOME_DIR/previous ]] && basename "$(dirname "$(readlink "$HOME_DIR/previous")")" || echo none)
    echo "Active: $current  Previous: $previous  Folder: $HOME_DIR"
}

cmd_prune() {
    local keep_current keep_previous dir
    keep_current=$(active_version)
    keep_previous=$([[ -L $HOME_DIR/previous ]] && basename "$(dirname "$(readlink "$HOME_DIR/previous")")" || true)
    for dir in "$VERSIONS"/*/; do
        dir=${dir%/}
        [[ -d $dir ]] || continue
        case $(basename "$dir") in "$keep_current"|"$keep_previous") continue ;; esac
        rm -rf "$dir"
        say "Removed $(basename "$dir")"
    done
}

case ${1:-} in
    install) shift; install_version "${1:-latest}" ;;
    update) cmd_update ;;
    rollback) cmd_rollback ;;
    start) shift; cmd_start "$@" ;;
    status) cmd_status ;;
    versions) ls -1 "$VERSIONS" 2>/dev/null || true ;;
    prune) cmd_prune ;;
    *) sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
