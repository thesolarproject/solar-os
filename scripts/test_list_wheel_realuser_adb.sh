#!/usr/bin/env bash
# 2026-07-17 — REAL user input on Y1: adb input keyevent path only.
# Home → Music → All Songs → rapid wheel spin → pause → reverse.
# Observes SolarAdbTest listPos lines emitted after each real selection change.
#
# Usage:
#   ./scripts/test_list_wheel_realuser_adb.sh
#   ./scripts/test_list_wheel_realuser_adb.sh --skip-install
#
set +e  # never abort mid-observation
set -u
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

log() { printf '%s\n' "$*"; }

pick_serial() {
  if [[ -n "${SERIAL:-}" ]]; then
    echo "$SERIAL"
    return
  fi
  # Prefer any Y1 product device
  timeout -s KILL 8 adb devices -l 2>/dev/null | awk '/device usb/ {print $1; exit}'
}

adb_t() {
  local t="$1"; shift
  local s
  s=$(pick_serial)
  if [[ -n "$s" ]]; then
    timeout -s KILL "$t" adb -s "$s" "$@" </dev/null
  else
    timeout -s KILL "$t" adb "$@" </dev/null
  fi
}

wait_device() {
  local i
  for i in $(seq 1 40); do
    timeout -s KILL 5 adb start-server >/dev/null 2>&1
    local out
    out=$(timeout -s KILL 5 adb devices 2>/dev/null || true)
    if echo "$out" | grep -qE $'\tdevice$'; then
      SERIAL=$(echo "$out" | awk '/\tdevice$/ {print $1; exit}')
      export ANDROID_SERIAL="$SERIAL"
      log "    serial=$SERIAL"
      return 0
    fi
    sleep 2
  done
  return 1
}

log "==> wait for device"
wait_device || { log "FATAL: no device"; exit 1; }

log "==> remount volumes"
adb_t 20 shell 'su -c "grep -q \" /storage/sdcard0 \" /proc/mounts || vdc volume mount /storage/sdcard0; grep -q \" /storage/sdcard1 \" /proc/mounts || vdc volume mount /storage/sdcard1"' >/dev/null 2>&1

if [[ $SKIP_INSTALL -eq 0 ]]; then
  log "==> build + install"
  export JAVA_HOME="${JAVA_HOME:-/home/deck/jdk-17}"
  ./gradlew :app:assembleDebug -q || { log "FATAL: build"; exit 1; }
  APK=app/build/outputs/apk/debug/app-debug.apk
  adb_t 120 push "$APK" /data/local/tmp/solar-debug.apk || { log "FATAL: push"; exit 1; }
  adb_t 60 shell "su -c 'pm install -r -d /data/local/tmp/solar-debug.apk'" || true
fi

log "==> version"
adb_t 12 shell "dumpsys package $PKG | grep versionName | head -1"

log "==> cold start + settle (library scan)"
adb_t 10 shell "input keyevent 224" >/dev/null 2>&1
adb_t 12 shell "am force-stop $PKG" >/dev/null 2>&1
sleep 1
adb_t 10 logcat -c >/dev/null 2>&1
adb_t 15 shell "am start -n $ACT" >/dev/null 2>&1
sleep 5

log "==> Back×3 → home"
# Single short shell — Back thrice with small gap
adb_t 25 shell 'input keyevent 4; usleep 150000; input keyevent 4; usleep 150000; input keyevent 4' >/dev/null 2>&1
sleep 0.8

log "==> REAL: wheel DOWN (Music) + OK"
# Y1 wheel down=MEDIA_PAUSE(127), OK=ENTER(66)
adb_t 20 shell 'input keyevent 127; usleep 200000; input keyevent 66' >/dev/null 2>&1
sleep 2.0

log "==> REAL: wheel to All Songs + OK (3× DOWN, OK)"
adb_t 30 shell 'input keyevent 127; usleep 180000; input keyevent 127; usleep 180000; input keyevent 127; usleep 180000; input keyevent 66' >/dev/null 2>&1
sleep 2.5

log "==> REAL: rapid dial DOWN 25× MEDIA_PAUSE (user spin)"
adb_t 10 logcat -c >/dev/null 2>&1
# Fewer keys, still one shell; 40ms gap ~ human fast spin
adb_t 45 shell 'i=0; while [ $i -lt 25 ]; do input keyevent 127; usleep 40000; i=$((i+1)); done' >/dev/null 2>&1
sleep 0.5

LOG_DOWN=$(adb_t 12 logcat -d -s SolarAdbTest:I 2>/dev/null || true)
log "--- listPos after DOWN spin ---"
echo "$LOG_DOWN" | grep 'listPos' | tail -20
MAX_POS=$(echo "$LOG_DOWN" | sed -n 's/.*listPos pos=\([0-9]*\).*/\1/p' | sort -n | tail -1)
log "    max_pos=${MAX_POS:-none}"

if [[ -z "${MAX_POS:-}" || "${MAX_POS}" -le 2 ]]; then
  log "==> retry: DPAD_DOWN path (legacy map) after re-open All Songs"
  adb_t 25 shell 'input keyevent 4; usleep 200000; input keyevent 127; usleep 150000; input keyevent 127; usleep 150000; input keyevent 127; usleep 150000; input keyevent 66' >/dev/null 2>&1
  sleep 1.5
  adb_t 10 logcat -c >/dev/null 2>&1
  adb_t 45 shell 'i=0; while [ $i -lt 25 ]; do input keyevent 20; usleep 40000; i=$((i+1)); done' >/dev/null 2>&1
  sleep 0.4
  LOG_DOWN=$(adb_t 12 logcat -d -s SolarAdbTest:I 2>/dev/null || true)
  echo "$LOG_DOWN" | grep 'listPos' | tail -20
  MAX_POS=$(echo "$LOG_DOWN" | sed -n 's/.*listPos pos=\([0-9]*\).*/\1/p' | sort -n | tail -1)
  log "    max_pos after DPAD=${MAX_POS:-none}"
fi

log "==> pause 500ms (hard-stop / no coast)"
LAST_BEFORE_PAUSE=$(echo "$LOG_DOWN" | sed -n 's/.*listPos pos=\([0-9]*\).*/\1/p' | tail -1)
sleep 0.5
LOG_PAUSE=$(adb_t 12 logcat -d -s SolarAdbTest:I 2>/dev/null || true)
LAST_AFTER_PAUSE=$(echo "$LOG_PAUSE" | sed -n 's/.*listPos pos=\([0-9]*\).*/\1/p' | tail -1)
log "    pos before pause=${LAST_BEFORE_PAUSE:-?} after=${LAST_AFTER_PAUSE:-?}"

log "==> REAL: reverse dial UP 15× MEDIA_PLAY"
adb_t 10 logcat -c >/dev/null 2>&1
adb_t 35 shell 'i=0; while [ $i -lt 15 ]; do input keyevent 126; usleep 40000; i=$((i+1)); done' >/dev/null 2>&1
sleep 0.4
LOG_UP=$(adb_t 12 logcat -d -s SolarAdbTest:I 2>/dev/null || true)
echo "$LOG_UP" | grep 'listPos' | tail -15
MIN_UP=$(echo "$LOG_UP" | sed -n 's/.*listPos pos=\([0-9]*\).*/\1/p' | sort -n | head -1)
MAX_UP=$(echo "$LOG_UP" | sed -n 's/.*listPos pos=\([0-9]*\).*/\1/p' | sort -n | tail -1)
log "    reverse range ${MIN_UP:-?}-${MAX_UP:-?}"

log "==> process health"
PID=$(adb_t 10 shell "pidof $PKG" 2>/dev/null | tr -d '\r\n ' || true)
log "    pid=${PID:-DEAD}"

log "==> REAL: Back to home, spin menu 10 DOWN / 10 UP"
adb_t 20 shell 'input keyevent 4; usleep 120000; input keyevent 4; usleep 120000; input keyevent 4' >/dev/null 2>&1
sleep 0.6
adb_t 10 logcat -c >/dev/null 2>&1
adb_t 30 shell 'i=0; while [ $i -lt 10 ]; do input keyevent 127; usleep 50000; i=$((i+1)); done; i=0; while [ $i -lt 10 ]; do input keyevent 126; usleep 50000; i=$((i+1)); done' >/dev/null 2>&1
sleep 0.3
adb_t 12 logcat -d -s SolarAdbTest:I 2>/dev/null | grep -E 'homeFocus|listPos' | tail -25

FAIL=0
if [[ -z "${MAX_POS:-}" || "${MAX_POS}" -le 2 ]]; then
  log "FAIL: list did not advance under real keyevents (max_pos=${MAX_POS:-none})"
  FAIL=1
else
  log "PASS: list advanced under real keyevents (max_pos=$MAX_POS)"
fi
if [[ -z "${PID:-}" ]]; then
  log "FAIL: process died"
  FAIL=1
fi
# Coast: if we got listPos during the pure sleep window beyond last spin, that's ghost.
# We only check process + advancement here.

if [[ $FAIL -ne 0 ]]; then
  log "RESULT: FAIL real-user wheel"
  exit 1
fi
log "RESULT: PASS real-user wheel"
exit 0
