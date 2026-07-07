#!/usr/bin/env bash
# 2026-07-06 — Push overlay hold timing patch (GlobalInputPolicy −30%) via su + /system.
# Usage: ./scripts/patch-overlay-hold-adb.sh [SERIAL] [--no-reboot]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB:-/home/deck/Android/Sdk/platform-tools/adb}"
SERIAL="${1:-${ANDROID_SERIAL:-${SOLAR_ADB_SERIAL:-}}}"
REBOOT=1
if [[ "${2:-}" == "--no-reboot" ]]; then REBOOT=0; fi
[[ -n "$SERIAL" ]] || { echo "usage: $0 SERIAL [--no-reboot]" >&2; exit 2; }

adb_ensure() {
  pkill -9 adb 2>/dev/null || true
  sleep 1
  "$ADB" start-server >/dev/null 2>&1 || true
  "$ADB" -s "$SERIAL" wait-for-device
}

adb_ensure

API="$("$ADB" -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
MODEL="$("$ADB" -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
echo "==> overlay hold patch → $SERIAL (model=$MODEL API=$API)"

"$ADB" -s "$SERIAL" shell "su -c id" | grep -q uid=0 || { echo "ERROR: su unavailable on $SERIAL" >&2; exit 1; }

if [[ "$API" == "17" || "$API" == "18" ]]; then
  BRIDGE="$ROOT/solar-rom/vendor/xposed/solar-context-bridge/SolarContextBridgeY1.apk"
  BRIDGE_SYS="/system/app/SolarContextBridgeY1.apk"
  BRIDGE_PKG="com.solar.launcher.xposed.bridge.y1"
else
  BRIDGE="$ROOT/solar-rom/vendor/xposed/solar-context-bridge/SolarContextBridgeY2.apk"
  BRIDGE_SYS="/system/app/SolarContextBridgeY2.apk"
  BRIDGE_PKG="com.solar.launcher.xposed.bridge.y2"
fi
COMPANION="$ROOT/global-context-modal/build/outputs/apk/debug/global-context-modal-debug.apk"
SOLAR="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$BRIDGE" && -f "$COMPANION" && -f "$SOLAR" ]] || {
  echo "Build first: build-context-bridge-apk.sh && ./gradlew :global-context-modal:assembleDebug :app:assembleDebug" >&2
  exit 1
}

"$ADB" -s "$SERIAL" shell "su -c 'mount -o remount,rw /system'"

echo "==> context bridge → $BRIDGE_SYS"
"$ADB" -s "$SERIAL" push "$BRIDGE" /data/local/tmp/solar-bridge.apk
"$ADB" -s "$SERIAL" shell "su -c 'cp /data/local/tmp/solar-bridge.apk $BRIDGE_SYS && chmod 644 $BRIDGE_SYS && rm -f /data/local/tmp/solar-bridge.apk'"
"$ADB" -s "$SERIAL" shell "su -c 'rm -rf /data/app/${BRIDGE_PKG}* /data/app-lib/${BRIDGE_PKG}*; pm install -r -f $BRIDGE_SYS'" | tr -d '\r'

adb_ensure

echo "==> companion → /system/app/SolarGlobalContextModal.apk"
"$ADB" -s "$SERIAL" push "$COMPANION" /data/local/tmp/solar-companion.apk
"$ADB" -s "$SERIAL" shell "su -c 'cp /data/local/tmp/solar-companion.apk /system/app/SolarGlobalContextModal.apk && chmod 644 /system/app/SolarGlobalContextModal.apk && rm -f /data/local/tmp/solar-companion.apk'"
"$ADB" -s "$SERIAL" shell "su -c 'rm -rf /data/app/com.solar.launcher.globalcontext*; pm install -r -f /system/app/SolarGlobalContextModal.apk'" | tr -d '\r'

adb_ensure

echo "==> Solar APK (user install)"
"$ADB" -s "$SERIAL" install -r -d -t "$SOLAR" | tr -d '\r'

"$ADB" -s "$SERIAL" shell "su -c sync"

adb_ensure

if [[ "$REBOOT" -eq 1 ]]; then
  echo "==> reboot (Xposed bridge reload)"
  "$ADB" -s "$SERIAL" reboot
  "$ADB" -s "$SERIAL" wait-for-device
  sleep 25
  echo "policy_rev=$("$ADB" -s "$SERIAL" shell getprop sys.solar.input.policy_rev | tr -d '\r')"
fi

echo "==> done — hold BACK ~0.42s in Settings/third-party for global modal"
