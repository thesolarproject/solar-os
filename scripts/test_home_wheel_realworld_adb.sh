#!/usr/bin/env bash
# 2026-07-17 — Real-world home menu wheel test on a connected Y1.
#
# Does NOT fake success: installs APK, starts Solar with in-app harness that drives
# the real onKeyDown wheel path, then asserts SolarAdbTest PASS home_wheel.
# Also fires a short external input keyevent storm and re-checks process health.
#
# Usage:
#   ./scripts/test_home_wheel_realworld_adb.sh
#   ./scripts/test_home_wheel_realworld_adb.sh --skip-install
#   ANDROID_SERIAL=SL246B8F23AB ./scripts/test_home_wheel_realworld_adb.sh
#
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PKG=com.solar.launcher
ACT="$PKG/.MainActivity"
SKIP_INSTALL=0
SERIAL="${ANDROID_SERIAL:-}"

# 2026-07-18 — Honour --skip-install / serial CLI (docs claimed it; parse was missing).
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

# Short ops (shell/logcat) — cap hung USB; install uses longer timeout below.
ADB_QUICK=(timeout 20 "${ADB[@]}")

log() { printf '%s\n' "$*"; }
die() { log "FATAL: $*"; exit 1; }

"${ADB_QUICK[@]}" wait-for-device || die "no device"
log "==> device: $("${ADB_QUICK[@]}" get-serialno 2>/dev/null || echo unknown)"

if [[ $SKIP_INSTALL -eq 0 ]]; then
  log "==> build + install"
  export JAVA_HOME="${JAVA_HOME:-/home/deck/jdk-17}"
  ./gradlew :app:assembleDebug -q || die "build failed"
  APK=app/build/outputs/apk/debug/app-debug.apk
  [[ -f "$APK" ]] || die "missing $APK"
  # 2026-07-18 — Push+pm on Y1 often exceeds 20s; was killed by ADB timeout wrapper.
  timeout 120 "${ADB[@]}" install -r -d "$APK" || die "install failed"
fi

log "==> clear logcat + force home wheel harness"
"${ADB_QUICK[@]}" logcat -c || true
"${ADB_QUICK[@]}" shell "am force-stop $PKG" || true
sleep 0.5
# Flag path works even when am start extras are stripped by the launcher
"${ADB_QUICK[@]}" shell "run-as $PKG sh -c 'echo 1 > files/adb_home_wheel.flag'" 2>/dev/null \
  || "${ADB_QUICK[@]}" shell "am start -n $ACT --ez solar_adb_home_wheel true" \
  || true
# Always also start with intent extra (covers both)
"${ADB_QUICK[@]}" shell "am start -n $ACT --ez solar_adb_home_wheel true --activity-single-top" || true

log "==> wait for SolarAdbTest home_wheel result (max 25s)"
PASS=0
FAIL=0
for i in $(seq 1 50); do
  LOG=$("${ADB_QUICK[@]}" logcat -d -s SolarAdbTest:I 2>/dev/null || true)
  if echo "$LOG" | grep -q 'PASS home_wheel'; then
    PASS=1
    break
  fi
  if echo "$LOG" | grep -q 'FAIL home_wheel'; then
    FAIL=1
    break
  fi
  sleep 0.5
done

log "==> SolarAdbTest home wheel lines"
"${ADB_QUICK[@]}" logcat -d -s SolarAdbTest:I 2>/dev/null | grep -E 'homeFocus|home_wheel|home_order|TIMING home' | tail -40 || true

if [[ $FAIL -eq 1 ]]; then
  log "HOME_WHEEL FAIL (in-app harness)"
  exit 1
fi
if [[ $PASS -ne 1 ]]; then
  log "HOME_WHEEL FAIL (timeout — no PASS home_wheel)"
  exit 1
fi

log "==> external keyevent storm (DPAD + MEDIA) while on home"
for _ in $(seq 1 15); do
  "${ADB[@]}" shell "input keyevent 20" || true   # DPAD_DOWN
done
for _ in $(seq 1 8); do
  "${ADB[@]}" shell "input keyevent 127" || true  # MEDIA_PAUSE (Y1 wheel CW often)
done
for _ in $(seq 1 15); do
  "${ADB[@]}" shell "input keyevent 19" || true   # DPAD_UP
done
for _ in $(seq 1 8); do
  "${ADB[@]}" shell "input keyevent 126" || true  # MEDIA_PLAY
done

sleep 1
FG=$("${ADB[@]}" shell "dumpsys window 2>/dev/null | grep mCurrentFocus" || true)
log "    focus: $FG"
if ! echo "$FG" | grep -q "$PKG"; then
  log "HOME_WHEEL FAIL — lost Solar focus after external keys"
  exit 1
fi

# Snapshot menu ids after storm
"${ADB[@]}" shell "am start -n $ACT --ez solar_adb_log_home_menu true --activity-single-top" || true
sleep 2.5
"${ADB[@]}" logcat -d -s SolarAdbTest:I 2>/dev/null | grep -E 'PASS home_menu|home_menu focus' | tail -5 || true

log "HOME_WHEEL PASS — in-app focus walk + external key storm survived"
exit 0
