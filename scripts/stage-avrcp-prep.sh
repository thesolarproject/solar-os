#!/usr/bin/env bash
# Stage AVRCP-patched system files for adb push or APK assets.
# Usage: stage-avrcp-prep.sh [OUTPUT_DIR]
#   KOENSAYR_SOURCE_SYS=/path/to/stock/system  — skip adb pull
#   KOENSAYR_FROM_DEVICE=1                    — adb pull live /system files (default when no SOURCE)
# set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh"

STAGING="${1:-$ROOT/build/avrcp-staging/system}"
KOENSAYR_SOURCE_SYS="${KOENSAYR_SOURCE_SYS:-}"
FROM_DEVICE="${KOENSAYR_FROM_DEVICE:-}"

SCRIPT_DIR="$ROOT/solar-rom/scripts"
APPLY="$SCRIPT_DIR/avrcp-apply-to-tree.sh"

die() { echo "error: $*" >&2; exit 1; }

command -v adb >/dev/null 2>&1 || die "adb required"
[[ -x "$APPLY" ]] || die "missing $APPLY"

AVRCP_PATHS=(
    app/MtkBt.odex
    bin/mtkbt
    lib/libextavrcp_jni.so
    lib/libextavrcp.so
    lib/libaudio.a2dp.default.so
    usr/keylayout/AVRCP.kl
    etc/bluetooth/audio.conf
    etc/bluetooth/auto_pairing.conf
    etc/bluetooth/blacklist.conf
    build.prop
)

pull_stock_tree() {
    local dest="$1"
    echo "==> Pulling stock /system files from device"
    adb get-state >/dev/null 2>&1 || die "no adb device"
    mkdir -p "$dest"
    for rel in "${AVRCP_PATHS[@]}"; do
        local dir
        dir="$(dirname "$rel")"
        mkdir -p "$dest/$dir"
        if adb shell "test -f /system/$rel" 2>/dev/null; then
            echo "  pull /system/$rel"
            adb pull "/system/$rel" "$dest/$rel" >/dev/null
        else
            echo "  skip (missing on device): /system/$rel"
        fi
    done
}

copy_source_tree() {
    local src="$1"
    local dest="$2"
    echo "==> Copying stock system tree from $src"
    [[ -d "$src" ]] || die "KOENSAYR_SOURCE_SYS not a directory: $src"
    mkdir -p "$dest"
    for rel in "${AVRCP_PATHS[@]}"; do
        if [[ -f "$src/$rel" ]]; then
            local dir
            dir="$(dirname "$rel")"
            mkdir -p "$dest/$dir"
            cp -a "$src/$rel" "$dest/$rel"
        fi
    done
}

rm -rf "$STAGING"
mkdir -p "$STAGING"

if [[ -n "$KOENSAYR_SOURCE_SYS" ]]; then
    copy_source_tree "$KOENSAYR_SOURCE_SYS" "$STAGING"
elif [[ "$FROM_DEVICE" == "1" ]] || adb get-state >/dev/null 2>&1; then
    pull_stock_tree "$STAGING"
else
    die "connect a Y1 via adb or set KOENSAYR_SOURCE_SYS"
fi

echo "==> Applying AVRCP patches to staging tree"
"$APPLY" "$STAGING"

echo "==> Staged AVRCP system tree: $STAGING"
echo "$STAGING"
