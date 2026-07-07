#!/usr/bin/env bash
# Clone or update rockbox-y1 source under reference/rockbox-y1 (gitignored).
# Usage: fetch-rockbox-y1-source.sh [DEST_DIR]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PIN_FILE="$SCRIPT_DIR/../patches/rockbox/source/SOURCE_PIN.txt"
DEST="${1:-$REPO_ROOT/reference/rockbox-y1}"
REPO_URL="https://github.com/rockbox-y1/rockbox.git"

die() { echo "error: $*" >&2; exit 1; }

branch="y1"
commit=""
if [[ -f "$PIN_FILE" ]]; then
    while IFS='=' read -r key val; do
        key="${key%%#*}"
        key="$(echo "$key" | tr -d '[:space:]')"
        val="$(echo "$val" | tr -d '[:space:]')"
        [[ -z "$key" ]] && continue
        case "$key" in
            branch) branch="$val" ;;
            commit) commit="$val" ;;
        esac
    done < "$PIN_FILE"
fi

if [[ -d "$DEST/.git" ]]; then
    echo "==> Updating rockbox-y1 source at $DEST (branch $branch)" >&2
    git -C "$DEST" fetch origin "$branch" --depth 1 >&2
    git -C "$DEST" checkout "$branch" >&2
    git -C "$DEST" pull --ff-only origin "$branch" >&2 || true
else
    echo "==> Cloning rockbox-y1 into $DEST" >&2
    mkdir -p "$(dirname "$DEST")"
    git clone --depth 1 --branch "$branch" "$REPO_URL" "$DEST" >&2
fi

if [[ -n "$commit" ]]; then
    git -C "$DEST" fetch origin "$commit" --depth 1 >&2 || true
    git -C "$DEST" checkout "$commit" >&2
fi

[[ -f "$DEST/android/src/org/rockbox/Helper/Connectivity.java" ]] \
    || die "rockbox-y1 source missing Connectivity.java"

printf '%s\n' "$DEST"
