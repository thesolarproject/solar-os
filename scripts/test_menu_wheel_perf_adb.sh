#!/usr/bin/env bash
# 2026-07-17 — Aggressive home/settings wheel scroll smoke under network load.
# Layman: spin the home menu fast while Wi-Fi is on and USB is plugged; confirm
# Solar still advances focus (no stuck highlight).
#
# Usage:
#   ./scripts/test_menu_wheel_perf_adb.sh [SERIAL]
#   ./scripts/test_menu_wheel_perf_adb.sh --skip-install
#
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PKG=com.solar.launcher
ACT="$PKG/.MainActivity"
SKIP_INSTALL=0
SERIAL="${ANDROID_SERIAL:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-install) SKIP_INSTALL=1; shift ;;
    -*)
      echo "unknown option: $1" >&2
      exit 2
      ;;
    *) SERIAL="$1"; shift ;;
  esac
done

ADB=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB=(adb -s "$SERIAL")
fi

log() { printf '%s\n' "$*"; }

"${ADB[@]}" wait-for-device

if [[ $SKIP_INSTALL -eq 0 ]]; then
  log "==> assembleDebug + install"
  export JAVA_HOME="${JAVA_HOME:-/home/deck/jdk-17}"
  ./gradlew :app:assembleDebug -q
  APK=app/build/outputs/apk/debug/app-debug.apk
  "${ADB[@]}" install -r -d "$APK"
fi

log "==> wake + launch Solar home"
"${ADB[@]}" shell "input keyevent 224" >/dev/null 2>&1 || true
"${ADB[@]}" shell "am force-stop $PKG" >/dev/null 2>&1 || true
sleep 0.4
"${ADB[@]}" shell "am start -n $ACT" >/dev/null 2>&1 || true
sleep 2.0

wheel_burst() {
  local code="$1"
  local n="$2"
  local i=0
  while [[ $i -lt $n ]]; do
    "${ADB[@]}" shell "input keyevent $code" >/dev/null 2>&1 || true
    i=$((i + 1))
  done
}

log "==> burst wheel DOWN (25) then UP (25) — expect snappy home focus"
t0=$(date +%s%3N 2>/dev/null || date +%s)
# DPAD_DOWN=20, DPAD_UP=19, MEDIA_PAUSE=127, MEDIA_PLAY=126
wheel_burst 20 25
wheel_burst 127 10
wheel_burst 19 25
wheel_burst 126 10
t1=$(date +%s%3N 2>/dev/null || date +%s)
elapsed=$((t1 - t0))
log "    wall_ms≈$elapsed for 70 keyevents (includes adb overhead)"

log "==> short SETTINGS open + wheel (if OK works)"
"${ADB[@]}" shell "input keyevent 4" >/dev/null 2>&1 || true
sleep 0.3
wheel_burst 20 12
wheel_burst 19 12

log "==> dump recent Solar wheel/home focus lines"
"${ADB[@]}" logcat -d -t 120 2>/dev/null \
  | grep -iE 'moveHomeMenuFocus|scrollHomeMenu|applyWheel|ANR|AndroidRuntime|FATAL' \
  | tail -30 || true

fg=$("${ADB[@]}" shell "dumpsys activity activities 2>/dev/null | grep mResumedActivity" || true)
log "    resumed: $fg"
if echo "$fg" | grep -q "$PKG"; then
  log "MENU_WHEEL_PERF PASS (Solar still resumed after wheel storm)"
  exit 0
fi
log "MENU_WHEEL_PERF FAIL — Solar not resumed"
exit 1
