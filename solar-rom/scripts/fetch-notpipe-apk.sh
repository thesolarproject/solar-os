#!/usr/bin/env bash
# 2026-07-06 — Download notPipe YouTube client APK for ROM bake + Solar platform self-heal.
# Layman: caches the third-party YouTube app Solar hooks for wheel-friendly playback.
# Technical: pinned v0.3.0 release; SHA256 verified before cache hit.
# Usage: fetch-notpipe-apk.sh [CACHE_DIR]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-root.sh"
ensure_sudo_shim_when_root

NOTPIPE_VERSION="${NOTPIPE_VERSION:-0.3.0}"
NOTPIPE_TAG="v${NOTPIPE_VERSION}"
NOTPIPE_APK_NAME="notPipe-${NOTPIPE_VERSION}-release.apk"
NOTPIPE_SHA256="${NOTPIPE_SHA256:-fbe3bd9a4ee5e7aea21102d81b5a4e579252479db0e1834abfb2c21e5fed7243}"
NOTPIPE_APK_URL="${NOTPIPE_APK_URL:-https://github.com/gohoski/notPipe/releases/download/${NOTPIPE_TAG}/${NOTPIPE_APK_NAME}}"
CACHE="${1:-${SOLAR_ROM_BUILD_DIR:-$HOME/.cache/solar-rom-build}/notpipe}"
OUT="$CACHE/$NOTPIPE_APK_NAME"

die() {
    echo "error: $*" >&2
    exit 1
}

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"
}

sha256_file() {
    sha256sum "$1" | awk '{print $1}'
}

if [ -f "$OUT" ]; then
    got="$(sha256_file "$OUT")"
    if [ "$got" = "$NOTPIPE_SHA256" ]; then
        echo "==> notPipe APK cached at $OUT" >&2
        printf '%s\n' "$CACHE"
        exit 0
    fi
    echo "==> stale notPipe cache (sha mismatch) — re-downloading" >&2
    rm -f "$OUT"
fi

require_cmd curl
require_cmd sha256sum
mkdir -p "$CACHE"

echo "==> Downloading notPipe $NOTPIPE_TAG from GitHub releases" >&2
curl -fsSL -o "$OUT" "$NOTPIPE_APK_URL"
got="$(sha256_file "$OUT")"
[ "$got" = "$NOTPIPE_SHA256" ] || die "notPipe APK sha256 mismatch (got $got)"

chmod 644 "$OUT"
echo "==> Cached notPipe at $OUT ($(du -sh "$OUT" | awk '{print $1}'))" >&2
printf '%s\n' "$CACHE"
