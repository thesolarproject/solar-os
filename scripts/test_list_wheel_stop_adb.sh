#!/usr/bin/env bash
# 2026-07-17 — In-app All Songs long-spin hard-stop + home wheel (no per-keyevent adb).
# Uses SolarAdbTest list_wheel / home_wheel harnesses — reliable on flaky Y1 adb.
#
# Usage:
#   ANDROID_SERIAL=SL246B8F23AB ./scripts/test_list_wheel_stop_adb.sh
#   ./scripts/test_list_wheel_stop_adb.sh --skip-install
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
    *) SERIAL="$1"; shift ;;
  esac
done

ADB=(timeout -s KILL 20 adb)
if [[ -n "$SERIAL" ]]; then
  ADB=(timeout -s KILL 20 adb -s "$SERIAL")
fi

log() { printf '%s\n' "$*"; }
die() { log "FATAL: $*"; exit 1; }

"${ADB[@]}" wait-for-device || die "no device"
log "==> device: $("${ADB[@]}" get-serialno 2>/dev/null || echo unknown)"

log "==> remount storage if needed"
"${ADB[@]}" shell 'su -c "grep -q /storage/sdcard0 /proc/mounts || vdc volume mount /storage/sdcard0; grep -q /storage/sdcard1 /proc/mounts || vdc volume mount /storage/sdcard1"' >/dev/null 2>&1 || true

if [[ $SKIP_INSTALL -eq 0 ]]; then
  log "==> assembleDebug + install"
  export JAVA_HOME="${JAVA_HOME:-/home/deck/jdk-17}"
  ./gradlew :app:assembleDebug -q || die "build failed"
  APK=app/build/outputs/apk/debug/app-debug.apk
  [[ -f "$APK" ]] || die "missing $APK"
  "${ADB[@]}" push "$APK" /data/local/tmp/solar-debug.apk || die "push failed"
  "${ADB[@]}" shell "su -c 'pm install -r -d /data/local/tmp/solar-debug.apk'" || die "install failed"
fi

wait_for_tag() {
  local needle="$1"
  local max="${2:-40}"
  local i
  for i in $(seq 1 "$max"); do
    LOG=$("${ADB[@]}" logcat -d -s SolarAdbTest:I 2>/dev/null || true)
    if echo "$LOG" | grep -q "PASS $needle"; then
      echo PASS
      return 0
    fi
    if echo "$LOG" | grep -q "FAIL $needle"; then
      echo FAIL
      return 0
    fi
    sleep 0.5
  done
  echo TIMEOUT
  return 0
}

log "==> list_wheel hard-stop harness"
"${ADB[@]}" logcat -c >/dev/null 2>&1 || true
"${ADB[@]}" shell "am force-stop $PKG" >/dev/null 2>&1 || true
sleep 0.5
"${ADB[@]}" shell "run-as $PKG sh -c 'echo 1 > files/adb_list_wheel.flag'" 2>/dev/null || true
# Give library a moment to scan after cold start if flag fires early
"${ADB[@]}" shell "am start -n $ACT --ez solar_adb_list_wheel true --activity-single-top" >/dev/null 2>&1 || true

LIST_R=$(wait_for_tag list_wheel 50)
log "==> list_wheel result: $LIST_R"
"${ADB[@]}" logcat -d -s SolarAdbTest:I 2>/dev/null | grep -E 'list_wheel|STEP|TIMING list' | tail -40 || true

log "==> home_wheel harness"
"${ADB[@]}" logcat -c >/dev/null 2>&1 || true
"${ADB[@]}" shell "am force-stop $PKG" >/dev/null 2>&1 || true
sleep 0.4
"${ADB[@]}" shell "run-as $PKG sh -c 'echo 1 > files/adb_home_wheel.flag'" 2>/dev/null || true
"${ADB[@]}" shell "am start -n $ACT --ez solar_adb_home_wheel true --activity-single-top" >/dev/null 2>&1 || true

HOME_R=$(wait_for_tag home_wheel 40)
log "==> home_wheel result: $HOME_R"
"${ADB[@]}" logcat -d -s SolarAdbTest:I 2>/dev/null | grep -E 'homeFocus|home_wheel|home_order|TIMING home' | tail -30 || true

if [[ "$LIST_R" != "PASS" ]]; then
  die "list_wheel $LIST_R"
fi
if [[ "$HOME_R" != "PASS" ]]; then
  die "home_wheel $HOME_R"
fi

log "RESULT: PASS (list_wheel + home_wheel)"
exit 0
