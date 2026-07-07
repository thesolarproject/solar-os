#!/usr/bin/env bash
# Hardware / adb audit for AOSP dialog + menu replacement (DialogHooks / AppMenuHooks).
# Usage: audit-dialog-replacement-adb.sh [adb_serial]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-adb-system.sh"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-xposed-install.sh"

adb_system_init
SERIAL="${1:-${ANDROID_SERIAL:-}}"
if [ -n "$SERIAL" ]; then
    export ANDROID_SERIAL="$SERIAL"
    adb_system_init
fi
ADB=("${SOLAR_ADB[@]}")

adb_sh() { "${ADB[@]}" shell "$@"; }

echo "==> audit-dialog-replacement (serial=${SERIAL:-default})"
timeout 8 "${ADB[@]}" get-state >/dev/null 2>&1 || { echo "FAIL: no adb device" >&2; exit 1; }

API=$(adb_sh getprop ro.build.version.sdk | tr -d '\r')
MODEL=$(adb_sh getprop ro.product.model | tr -d '\r')
echo "device: $MODEL API=$API"

errors=0
pass() { echo "  OK $*"; }
fail() { echo "  FAIL $*" >&2; errors=$((errors + 1)); }

# Framework + modules
if adb_sh "[ -f /system/framework/XposedBridge.jar ] && echo yes" 2>/dev/null | grep -q yes; then
    pass "/system/framework/XposedBridge.jar"
else
    fail "XposedBridge.jar missing"
fi

if xposed_app_process_active_via_adb 2>/dev/null; then
    pass "app_process has Xposed support"
elif adb_sh "su -c 'grep -aq \"with Xposed support\" /system/bin/app_process && echo yes'" 2>/dev/null | tr -d '\r' | grep -q yes; then
    pass "app_process has Xposed support"
elif adb_sh "[ -f /system/bin/app_process.xposed.staged ] && echo yes" 2>/dev/null | tr -d '\r' | grep -q yes; then
    echo "  WARN app_process staged for reboot"
else
    fail "app_process not Xposed-patched"
fi

BRIDGE_PKG="$(xposed_context_bridge_pkg_for_api "$API" 2>/dev/null || true)"
if [ -z "$BRIDGE_PKG" ]; then
    BRIDGE_PKG="com.solar.launcher.xposed.bridge.y1"
    [ "$API" -ge 19 ] && BRIDGE_PKG="com.solar.launcher.xposed.bridge.y2"
fi

if adb_sh pm path "$BRIDGE_PKG" 2>/dev/null | grep -q package; then
    pass "bridge installed ($BRIDGE_PKG)"
else
    fail "bridge not installed ($BRIDGE_PKG)"
fi

if adb_sh pm path com.solar.launcher.xposed.themefont 2>/dev/null | grep -q package; then
    pass "SolarThemeFont installed"
else
    fail "SolarThemeFont missing"
fi

if adb_sh su -c "grep -q '$BRIDGE_PKG' /data/data/de.robv.android.xposed.installer/shared_prefs/enabled_modules.xml 2>/dev/null && echo yes" 2>/dev/null | grep -q yes; then
    pass "bridge enabled in Xposed prefs"
else
    fail "bridge not enabled — run 99XposedInit.sh or enable-xposed-module-adb.sh"
fi

# Solar overlay host
if adb_sh pm path com.solar.launcher 2>/dev/null | grep -q package; then
    pass "Solar launcher installed"
else
    fail "com.solar.launcher missing"
fi

# Sidecars for Holo fail-open cosmetics
for root in /storage/sdcard1 /storage/sdcard0 /sdcard; do
    if adb_sh "[ -f $root/.solar/system-font.ttf ] && echo yes" 2>/dev/null | grep -q yes; then
        pass "font sidecar at $root/.solar/system-font.ttf"
        break
    fi
done

# Logcat probe — bridge should log hook install in a third-party process after Settings launch
echo "==> logcat probe (Settings launch)"
adb_sh am force-stop com.android.settings 2>/dev/null || true
"${ADB[@]}" logcat -c 2>/dev/null || true
adb_sh am start -n com.android.settings/.Settings >/dev/null 2>&1 || true
sleep 2
HOOK_LOG=$("${ADB[@]}" logcat -d 2>/dev/null | grep -c 'SolarCtxBridge.*hooked' || true)
if [ "${HOOK_LOG:-0}" -gt 0 ]; then
    pass "SolarCtxBridge hook logs present ($HOOK_LOG lines)"
else
    echo "  WARN no SolarCtxBridge hook lines in logcat (reboot after module update?)"
fi

echo ""
echo "==> Manual wheel matrix (complete on hardware)"
echo "  [ ] Settings overflow menu → Solar list, pick works, app continues"
echo "  [ ] Third-party AlertDialog OK/Cancel → Solar themed, callbacks fire"
echo "  [ ] Simple item list (setItems) → Solar list overlay"
echo "  [ ] Submenu row → nested menu or re-intercept"
echo "  [ ] Back on overlay → cancel (-1), no app crash"
echo "  [ ] Xposed Installer → stock Holo dialogs (denylist)"
echo "  [ ] Progress / multi-choice → stock Holo (fail-open + theme skin)"
echo "  [ ] Third-party ANR → Solar overlay (Wait / Close / Report), wheel navigates"
echo "  [ ] Third-party crash → Solar overlay (Close / Report), scrollable detail body"
echo "  [ ] Solar ANR → auto-WAIT then Solar overlay (not stock Holo)"
echo "  [ ] System / fail-open ANR → stock Holo + wheel/side/center → DPAD focus"

if [ "$errors" -gt 0 ]; then
    echo "==> audit-dialog-replacement: $errors failure(s)" >&2
    exit 1
fi
echo "==> audit-dialog-replacement: automated checks passed"
