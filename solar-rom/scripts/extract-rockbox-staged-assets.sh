#!/usr/bin/env bash
# Extract lib/armeabi/*.so and libmisc .rockbox tree from org.rockbox.apk for on-device copy (no unzip on Y2).
# Patches staged librockbox.so for Y2 native system() shims (APK lib stays pristine).
# Usage: extract-rockbox-staged-assets.sh INPUT.apk OUT_LIBS_DIR OUT_DOT_ROCKBOX_DIR
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IN="${1:-}"
OUT_LIBS="${2:-}"
OUT_RB="${3:-}"

die() { echo "error: $*" >&2; exit 1; }

[ -n "$IN" ] && [ -f "$IN" ] || die "usage: $0 INPUT.apk OUT_LIBS_DIR OUT_DOT_ROCKBOX_DIR"
[ -n "$OUT_LIBS" ] && [ -n "$OUT_RB" ] || die "usage: $0 INPUT.apk OUT_LIBS_DIR OUT_DOT_ROCKBOX_DIR"

command -v unzip >/dev/null 2>&1 || die "missing unzip (host build only)"

WORK="$(mktemp -d "${TMPDIR:-/tmp}/rockbox-stage-XXXXXX")"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT

mkdir -p "$OUT_LIBS" "$OUT_RB"
rm -rf "$OUT_LIBS"/* "$OUT_RB"/*

unzip -o -q "$IN" 'lib/armeabi/*' -d "$WORK/apk"
mv "$WORK/apk/lib/armeabi/"*.so "$OUT_LIBS/"

unzip -o -q -p "$IN" lib/armeabi/libmisc.so > "$WORK/libmisc.so"
unzip -o -q "$WORK/libmisc.so" 'sdcard/.rockbox/*' -d "$WORK"
cp -a "$WORK/sdcard/.rockbox/." "$OUT_RB/"

[ -f "$OUT_LIBS/librockbox.so" ] || die "missing librockbox.so in staged libs"
[ -f "$OUT_RB/rocks/viewers/db_folder_select.rock" ] \
    || die "missing db_folder_select.rock in staged .rockbox"

# Y2: patch runtime JNI copy only — Dalvik Xposed cannot hook native system() in org.rockbox.
PATCH_LIB="$SCRIPT_DIR/../patches/rockbox/patch_librockbox_system.py"
if [ -f "$PATCH_LIB" ]; then
    python3 "$PATCH_LIB" "$OUT_LIBS/librockbox.so"
    # 2026-07-05: grep without -q so it reads all of strings' output — with `set -o pipefail`,
    # grep -q quitting at first match sent strings SIGPIPE (141) and falsely failed this check.
    # Was: grep -q (early-exit). Now: full-read grep with stdout discarded — same true/false answer, no race.
    strings "$OUT_LIBS/librockbox.so" 2>/dev/null | grep 'solar-rb-launch' >/dev/null \
        || die "staged librockbox.so missing solar-rb-launch after patch"
fi

echo "==> Staged Rockbox libs ($(ls "$OUT_LIBS" | wc -l) .so) and .rockbox tree at $OUT_RB"
