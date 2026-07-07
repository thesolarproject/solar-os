#!/usr/bin/env bash
# 2026-07-05 — Cache MediaTek FMRadio.apk + libfm*.so from rockbox-y1 type-A base for Y2 ROM bake.
# Layman: Y2 stock firmware has no FM app; copy Y1's hardware FM stack so Settings → FM Radio works.
# Technical: Solar FmEngine binds com.mediatek.FMRadio.IFMRadioService — needs APK + libfmjni on /system.
# Reversal: delete script + install_fm_from_y1_base hook; Y2 loses stock Settings FM entry again.
# Usage: fetch-fm-mtk-from-y1-base.sh [CACHE_DIR]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-root.sh"
ensure_sudo_shim_when_root
CACHE="${1:-${SOLAR_ROM_BUILD_DIR:-$HOME/.cache/solar-rom-build}/fm-mtk-y1-y2}"
BASE_URL="https://github.com/rockbox-y1/rockbox/releases/download/type-a-base/rom.zip"

die() {
    echo "error: $*" >&2
    exit 1
}

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"
}

# Reuse cache when FM APK + core JNI lib are already present.
if [ -f "$CACHE/FMRadio.apk" ] && [ -f "$CACHE/libfmjni.so" ]; then
    echo "==> MediaTek FM assets cached at $CACHE" >&2
    printf '%s\n' "$CACHE"
    exit 0
fi

require_cmd curl
require_cmd unzip
require_cmd sudo
HAS_SIMG2IMG=0
command -v simg2img >/dev/null 2>&1 && HAS_SIMG2IMG=1

WORK="$(mktemp -d "${SOLAR_ROM_BUILD_DIR:-$HOME/.cache/solar-rom-build}/fm-mtk-y1-y2-XXXXXX")"
cleanup() {
    if mountpoint -q "$WORK/mount" 2>/dev/null; then
        sudo umount "$WORK/mount" 2>/dev/null || true
    fi
    rm -rf "$WORK"
}
trap cleanup EXIT

echo "==> Downloading rockbox-y1 type-A base for FMRadio.apk + libfm*" >&2
curl -fsSL -o "$WORK/rom.zip" "$BASE_URL"

echo "==> Extracting system.img from type-A base" >&2
unzip -q "$WORK/rom.zip" system.img -d "$WORK" || unzip -q "$WORK/rom.zip" '*/system.img' -d "$WORK"
SYSIMG="$(find "$WORK" -name system.img | head -1)"
[ -f "$SYSIMG" ] || die "type-A base zip missing system.img"

MOUNT_IMG="$SYSIMG"
if [ "$HAS_SIMG2IMG" = "1" ] && file "$SYSIMG" 2>/dev/null | grep -qi 'android sparse'; then
    echo "==> Converting sparse type-A system.img to raw for loop mount" >&2
    MOUNT_IMG="$WORK/system.raw.img"
    simg2img "$SYSIMG" "$MOUNT_IMG"
fi

sudo modprobe loop max_loop=64 2>/dev/null || true
mkdir -p "$WORK/mount" "$CACHE"
echo "==> Mounting Y1 system.img read-only (FM extract)" >&2
sudo mount -o loop,ro "$MOUNT_IMG" "$WORK/mount"
[ -f "$WORK/mount/app/FMRadio.apk" ] || die "Y1 base missing /system/app/FMRadio.apk"
[ -f "$WORK/mount/lib/libfmjni.so" ] || die "Y1 base missing /system/lib/libfmjni.so"

cp "$WORK/mount/app/FMRadio.apk" "$CACHE/FMRadio.apk"
[ -f "$WORK/mount/app/FMRadio.odex" ] && cp "$WORK/mount/app/FMRadio.odex" "$CACHE/FMRadio.odex" || true
for so in "$WORK/mount/lib"/libfm*.so; do
    [ -f "$so" ] || continue
    cp "$so" "$CACHE/$(basename "$so")"
done
chmod 644 "$CACHE"/*.apk "$CACHE"/*.so 2>/dev/null || true
chmod 644 "$CACHE/FMRadio.apk" "$CACHE"/libfm*.so

echo "==> Cached MediaTek FM at $CACHE ($(du -sh "$CACHE" | awk '{print $1}'))" >&2
printf '%s\n' "$CACHE"
