#!/usr/bin/env bash
# 2026-07-06 — Install notPipe + SolarNotPipeBridge on rooted Y1/Y2 via adb.
# Layman: puts the YouTube helper app and its Solar hook on the device.
# Usage: install-notpipe-adb.sh [--no-bridge]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-adb-system.sh"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-xposed-install.sh"

INSTALL_BRIDGE=1
CLEAN=0
while [ $# -gt 0 ]; do
    case "$1" in
        --no-bridge) INSTALL_BRIDGE=0; shift ;;
        --clean) CLEAN=1; shift ;;
        *) echo "usage: $0 [--clean] [--no-bridge]" >&2; exit 1 ;;
    esac
done

adb_system_init
adb_system_preflight

chmod +x "$SCRIPT_DIR/fetch-notpipe-apk.sh"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REF_APK="$ROOT/reference/NotPipe reference/notPipe-0.3.0-release.apk"
if [ -f "$REF_APK" ]; then
    NOTPIPE_APK="$REF_APK"
    echo "==> Using reference notPipe APK: $NOTPIPE_APK"
else
    NOTPIPE_CACHE="$("$SCRIPT_DIR/fetch-notpipe-apk.sh")"
    NOTPIPE_APK="$NOTPIPE_CACHE/notPipe-0.3.0-release.apk"
fi
[ -f "$NOTPIPE_APK" ] || { echo "missing notPipe APK (reference or cache)" >&2; exit 1; }

SYSTEM_NOTPIPE="/system/app/io.github.gohoski.notpipe.apk"

if [ "$CLEAN" -eq 1 ]; then
    echo "==> Clean uninstall notPipe + bridge (remove SD stray copies)"
    adb_su_sh "pm uninstall io.github.gohoski.notpipe 2>/dev/null || true"
    adb_su_sh "pm uninstall com.solar.launcher.xposed.notpipe 2>/dev/null || true"
    adb_su_sh "rm -f /storage/sdcard0/Android/data/io.github.gohoski.notpipe* 2>/dev/null || true"
    adb_su_sh "find /storage/sdcard0 -maxdepth 4 -iname '*notpipe*.apk' -delete 2>/dev/null || true"
    adb_su_sh "pm set-install-location 1"
fi

echo "==> Install notPipe (io.github.gohoski.notpipe)"
if adb_push_to_system "$NOTPIPE_APK" "$SYSTEM_NOTPIPE" 644; then
    adb_pm_install_internal "$SYSTEM_NOTPIPE" "io.github.gohoski.notpipe" || true
else
    echo "WARN: /system push failed — cannot install notPipe without system partition" >&2
    exit 1
fi
adb_su_sh "pm path io.github.gohoski.notpipe" || true

if [ "$INSTALL_BRIDGE" -eq 1 ]; then
    notpipe_bridge_apk="$(xposed_notpipe_bridge_apk "$SCRIPT_DIR")" || exit 1
    echo "==> Install SolarNotPipeBridge ($XPOSED_NOTPIPE_BRIDGE_PKG)"
    if adb_push_to_system "$notpipe_bridge_apk" "$XPOSED_SYSTEM_NOTPIPE_BRIDGE_APK" 644; then
        adb_pm_install_internal "$XPOSED_SYSTEM_NOTPIPE_BRIDGE_APK" "$XPOSED_NOTPIPE_BRIDGE_PKG" || true
        xposed_ensure_module_enabled_via_adb "$XPOSED_NOTPIPE_BRIDGE_PKG" || true
    fi
    adb_su_sh "pm path $XPOSED_NOTPIPE_BRIDGE_PKG" || true
fi

echo "==> notPipe adb install complete — reboot once for Xposed bridge hooks"
