#!/usr/bin/env bash
# 2026-07-06 — Verify bundled Rockbox platform assets (Y2 manifest patch + staged native shim).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RB="$ROOT/app/src/main/assets/platform/rockbox"

die() { echo "verify-platform-rockbox-assets: $*" >&2; exit 1; }

[ -f "$RB/org.rockbox-y1.apk" ] || die "missing org.rockbox-y1.apk"
[ -f "$RB/org.rockbox-y2.apk" ] || die "missing org.rockbox-y2.apk"
[ -f "$RB/librockbox-system.so" ] || die "missing librockbox-system.so"
[ -f "$RB/staged-libs/librockbox.so" ] || die "missing staged-libs/librockbox.so"
[ -f "$RB/dot-rockbox/rocks/viewers/db_folder_select.rock" ] \
    || die "missing dot-rockbox db_folder_select.rock"
[ -f "$RB/solar-rb-launch" ] || die "missing solar-rb-launch"
[ -f "$RB/rockbox-y2-config.cfg" ] || die "missing rockbox-y2-config.cfg"
[ -f "$RB/stage-index.json" ] || die "missing stage-index.json"
[ -x "$RB/sync-rockbox-libs.sh" ] || die "missing sync-rockbox-libs.sh"
[ -x "$RB/sync-rockbox-assets.sh" ] || die "missing sync-rockbox-assets.sh"

if ! strings "$RB/staged-libs/librockbox.so" 2>/dev/null | grep 'solar-rb-launch' >/dev/null; then
    die "staged librockbox.so missing solar-rb-launch strings"
fi

apk_lib="$(mktemp)"
unzip -o -q -p "$RB/org.rockbox-y2.apk" lib/armeabi/librockbox.so > "$apk_lib" 2>/dev/null || true
if [ -s "$apk_lib" ]; then
    if strings "$apk_lib" 2>/dev/null | grep 'solar-rb-launch' >/dev/null; then
        rm -f "$apk_lib"
        die "Y2 APK librockbox.so must stay pristine"
    fi
fi
rm -f "$apk_lib"

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
AAPT=""
for v in 35.0.0 34.0.0 36.0.0 28.0.3; do
    [ -x "$ANDROID_HOME/build-tools/$v/aapt" ] && AAPT="$ANDROID_HOME/build-tools/$v/aapt" && break
done
if [ -n "$AAPT" ]; then
    if "$AAPT" dump xmltree "$RB/org.rockbox-y2.apk" AndroidManifest.xml 2>/dev/null | grep -q 'sharedUserId'; then
        die "org.rockbox-y2.apk still has sharedUserId"
    fi
fi

echo "verify-platform-rockbox-assets: OK"
