#!/usr/bin/env bash
# Download org.rockbox.apk + librockbox.so from rockbox-y1 type-A base for Y2 ROM builds.
# Caches under ~/.cache/solar-rom-build/rockbox-y1-y2/ — not vendored in git.
# Usage: fetch-rockbox-y1-y2-assets.sh [CACHE_DIR]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-root.sh"
ensure_sudo_shim_when_root
CACHE="${1:-${SOLAR_ROM_BUILD_DIR:-$HOME/.cache/solar-rom-build}/rockbox-y1-y2}"
BASE_URL="https://github.com/rockbox-y1/rockbox/releases/download/type-a-base/rom.zip"

die() {
    echo "error: $*" >&2
    exit 1
}

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"
}

# Reuse cache when both assets are already present from a prior build.
if [ -f "$CACHE/org.rockbox.apk" ] && [ -f "$CACHE/librockbox.so" ]; then
    echo "==> Rockbox-y1 assets cached at $CACHE" >&2
    printf '%s\n' "$CACHE"
    exit 0
fi

require_cmd curl
require_cmd unzip
require_cmd sudo
if command -v simg2img >/dev/null 2>&1; then
    HAS_SIMG2IMG=1
else
    HAS_SIMG2IMG=0
fi

WORK="$(mktemp -d "${SOLAR_ROM_BUILD_DIR:-$HOME/.cache/solar-rom-build}/rockbox-y1-y2-XXXXXX")"
cleanup() {
    if mountpoint -q "$WORK/mount" 2>/dev/null; then
        sudo umount "$WORK/mount" 2>/dev/null || true
    fi
    rm -rf "$WORK"
}
trap cleanup EXIT

echo "==> Downloading rockbox-y1 type-A base for org.rockbox.apk" >&2
curl -fsSL -o "$WORK/rom.zip" "$BASE_URL"

echo "==> Extracting system.img from type-A base" >&2
unzip -q "$WORK/rom.zip" system.img -d "$WORK" || unzip -q "$WORK/rom.zip" '*/system.img' -d "$WORK"
SYSIMG="$(find "$WORK" -name system.img | head -1)"
[ -f "$SYSIMG" ] || die "type-A base zip missing system.img"

# Type-A bases may ship sparse on some mirrors — loop mount needs raw ext4.
MOUNT_IMG="$SYSIMG"
if [ "$HAS_SIMG2IMG" = "1" ] && file "$SYSIMG" 2>/dev/null | grep -qi 'android sparse'; then
    echo "==> Converting sparse type-A system.img to raw for loop mount" >&2
    MOUNT_IMG="$WORK/system.raw.img"
    simg2img "$SYSIMG" "$MOUNT_IMG"
fi

sudo modprobe loop max_loop=64 2>/dev/null || true
mkdir -p "$WORK/mount" "$CACHE"
echo "==> Mounting Y1 system.img read-only" >&2
sudo mount -o loop,ro "$MOUNT_IMG" "$WORK/mount"
[ -f "$WORK/mount/app/org.rockbox.apk" ] || die "Y1 base missing /system/app/org.rockbox.apk"
[ -f "$WORK/mount/lib/librockbox.so" ] || die "Y1 base missing /system/lib/librockbox.so"

cp "$WORK/mount/app/org.rockbox.apk" "$CACHE/org.rockbox.apk"
cp "$WORK/mount/lib/librockbox.so" "$CACHE/librockbox.so"
chmod 644 "$CACHE/org.rockbox.apk" "$CACHE/librockbox.so"

echo "==> Cached rockbox-y1 assets at $CACHE ($(du -sh "$CACHE" | awk '{print $1}'))" >&2
printf '%s\n' "$CACHE"
