#!/usr/bin/env bash
# 2026-07-16 — Exhaustive real-world usage matrix on connected Y1/Y2 (and optional emulators).
#
# Walks library modes, search, muted play, playlists, favorites, settings, Get Music,
# context menus, and rapid screen flips via in-app harness (SolarAdbTest logcat).
# Then runs keyevent permutation suites that approximate real wheel/OK/Back paths.
#
# Usage:
#   ./scripts/test_realworld_matrix_adb.sh                  # all device-state serials
#   ./scripts/test_realworld_matrix_adb.sh SERIAL [SERIAL…]
#   ./scripts/test_realworld_matrix_adb.sh --skip-install
#   ./scripts/test_realworld_matrix_adb.sh --matrix-only     # skip keyevent phase
#   ./scripts/test_realworld_matrix_adb.sh --keys-only       # skip in-app matrix
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PKG="com.solar.launcher"
ACT="$PKG/.MainActivity"
APK_DEBUG="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
RUN_ID="$(date +%Y%m%d-%H%M%S)"
OUT_ROOT="$ROOT/dist/realworld-adb-tests/$RUN_ID"
mkdir -p "$OUT_ROOT"

SKIP_INSTALL=0
MATRIX_ONLY=0
KEYS_ONLY=0
CLI_SERIALS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-install) SKIP_INSTALL=1; shift ;;
    --matrix-only) MATRIX_ONLY=1; shift ;;
    --keys-only) KEYS_ONLY=1; shift ;;
    -h|--help)
      sed -n '2,18p' "$0"
      exit 0
      ;;
    --) shift; CLI_SERIALS+=("$@"); break ;;
    -*)
      echo "ERROR: unknown option: $1" >&2
      exit 2
      ;;
    *) CLI_SERIALS+=("$1"); shift ;;
  esac
done

log() { printf '%s\n' "$*" | tee -a "$OUT_ROOT/run.log"; }
die() { log "FATAL: $*"; exit 1; }

adb_s() {
  local serial="$1"
  shift
  # Never steal stdin from loops.
  timeout 45 adb -s "$serial" "$@" </dev/null
}

# Volume all the way down (Y1/Y2 lack `media` CLI).
mute_device() {
  local s="$1"
  # STREAM_MUSIC=3; service call indices vary by API — hammer VOL_DOWN + mute key too.
  adb_s "$s" shell "input keyevent 164" >/dev/null 2>&1 || true
  adb_s "$s" shell 'i=0; while [ $i -lt 20 ]; do input keyevent 25; i=$((i+1)); done' >/dev/null 2>&1 || true
  # Best-effort AudioManager via app_process is flaky; mute is also enforced in-app matrix.
}

stay_awake() {
  local s="$1"
  adb_s "$s" shell "settings put system screen_off_timeout 1800000" >/dev/null 2>&1 || true
  adb_s "$s" shell "svc power stayon true" >/dev/null 2>&1 || true
  adb_s "$s" shell "input keyevent 224" >/dev/null 2>&1 || true
}

list_serials() {
  if [[ ${#CLI_SERIALS[@]} -gt 0 ]]; then
    printf '%s\n' "${CLI_SERIALS[@]}"
    return
  fi
  adb devices 2>/dev/null | awk '/\tdevice$/{print $1}'
}

mapfile -t SERIALS < <(list_serials)
[[ ${#SERIALS[@]} -gt 0 ]] || die "no adb devices in 'device' state"

log "==> Real-world matrix run $RUN_ID"
log "    devices: ${SERIALS[*]}"
log "    out: $OUT_ROOT"

if [[ $SKIP_INSTALL -eq 0 ]]; then
  log "==> Building installDebug"
  ./gradlew :app:installDebug -q 2>&1 | tee -a "$OUT_ROOT/build.log" || {
    log "WARN: gradlew installDebug failed — trying adb install of existing APK"
    [[ -f "$APK_DEBUG" ]] || die "no $APK_DEBUG"
    for s in "${SERIALS[@]}"; do
      log "    install $s"
      adb_s "$s" install -r -d "$APK_DEBUG" 2>&1 | tee -a "$OUT_ROOT/install-$s.log" || true
    done
  }
else
  log "==> --skip-install (using APK already on devices)"
fi

# ---------- In-app matrix ----------
run_matrix_on() {
  local s="$1"
  local d="$OUT_ROOT/$s"
  mkdir -p "$d"
  log "---- MATRIX $s ----"
  stay_awake "$s"
  mute_device "$s"
  adb_s "$s" logcat -c >/dev/null 2>&1 || true
  adb_s "$s" shell "am force-stop $PKG" >/dev/null 2>&1 || true
  sleep 1
  # Flag file path works when HOME launcher strips am-start extras.
  adb_s "$s" shell "run-as $PKG sh -c 'mkdir -p files; touch files/adb_realworld_matrix.flag'" \
    >/dev/null 2>&1 || true
  adb_s "$s" shell "am start -S -n $ACT --ez solar_adb_realworld_matrix true" \
    >/dev/null 2>&1 || adb_s "$s" shell "monkey -p $PKG -c android.intent.category.LAUNCHER 1" \
    >/dev/null 2>&1 || true

  # Matrix ~36 steps × ~0.5–1.2s ≈ 30–50s; wait up to 90s for matrix_done.
  local waited=0
  local done=0
  while [[ $waited -lt 90 ]]; do
    sleep 3
    waited=$((waited + 3))
    if adb_s "$s" logcat -d -s SolarAdbTest:I 2>/dev/null | grep -qE 'matrix_done|FAIL matrix'; then
      done=1
      break
    fi
  done
  adb_s "$s" logcat -d -s SolarAdbTest:I AndroidRuntime:E solar:E 2>/dev/null \
    | tr -d '\r' > "$d/matrix-logcat.txt" || true
  adb_s "$s" shell "dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity|mFocusedActivity' | head -5" \
    2>/dev/null | tr -d '\r' > "$d/focus.txt" || true

  local pass fail steps
  pass=$(grep -c ' PASS ' "$d/matrix-logcat.txt" 2>/dev/null || echo 0)
  fail=$(grep -c ' FAIL ' "$d/matrix-logcat.txt" 2>/dev/null || echo 0)
  steps=$(grep -c ' STEP ' "$d/matrix-logcat.txt" 2>/dev/null || echo 0)
  if grep -q 'PASS matrix_done' "$d/matrix-logcat.txt" 2>/dev/null; then
    log "    MATRIX PASS $s  pass=$pass fail=$fail steps=$steps waited=${waited}s"
    echo PASS > "$d/matrix.result"
  elif [[ $done -eq 1 ]]; then
    log "    MATRIX FAIL $s  pass=$pass fail=$fail steps=$steps (see matrix-logcat.txt)"
    echo FAIL > "$d/matrix.result"
  else
    log "    MATRIX TIMEOUT $s  pass=$pass fail=$fail steps=$steps waited=${waited}s"
    echo TIMEOUT > "$d/matrix.result"
  fi
  # Always mute again after play steps.
  mute_device "$s"
}

# ---------- Keyevent real-user permutations ----------
# Keycodes: BACK=4 CENTER=23 ENTER=66 DPAD_UP=19 DPAD_DOWN=20
# MEDIA_PLAY=126 MEDIA_PAUSE=127 (Y1 wheel via kl)
key() {
  local s="$1" code="$2"
  adb_s "$s" shell "input keyevent $code" >/dev/null 2>&1 || true
  sleep 0.12
}

keys_burst_down() {
  local s="$1" n="${2:-3}"
  local i=0
  while [[ $i -lt $n ]]; do
    key "$s" 20
    key "$s" 127
    i=$((i + 1))
  done
}

keys_burst_up() {
  local s="$1" n="${2:-2}"
  local i=0
  while [[ $i -lt $n ]]; do
    key "$s" 19
    key "$s" 126
    i=$((i + 1))
  done
}

ok() {
  local s="$1"
  key "$s" 23
  key "$s" 66
}

back() {
  local s="$1"
  key "$s" 4
}

run_keys_on() {
  local s="$1"
  local d="$OUT_ROOT/$s"
  mkdir -p "$d"
  log "---- KEYS $s ----"
  stay_awake "$s"
  mute_device "$s"
  adb_s "$s" logcat -c >/dev/null 2>&1 || true
  adb_s "$s" shell "am start -n $ACT --ez solar_adb_goto true --ei solar_adb_screen 1" \
    >/dev/null 2>&1 || true
  sleep 2

  # P1: Home wheel traverse + open first few rows (Music / Get Music / etc.)
  local p
  for p in 1 2 3 4 5 6 7 8; do
    keys_burst_down "$s" 1
    ok "$s"
    sleep 0.45
    # Sub-scroll inside screens
    keys_burst_down "$s" 4
    keys_burst_up "$s" 2
    ok "$s"
    sleep 0.35
    keys_burst_down "$s" 2
    back "$s"
    sleep 0.25
    back "$s"
    sleep 0.2
  done

  # P2: Force Music library (STATE_BROWSER=2) then drill each root row by index
  adb_s "$s" shell "am start -n $ACT --ez solar_adb_goto true --ei solar_adb_screen 2" \
    >/dev/null 2>&1 || true
  sleep 1.5
  for p in 0 1 2 3 4 5 6 7 8 9 10; do
    # Reset to browser root via double back then goto
    back "$s"; back "$s"
    adb_s "$s" shell "am start -n $ACT --ez solar_adb_goto true --ei solar_adb_screen 2" \
      >/dev/null 2>&1 || true
    sleep 0.8
    local j=0
    while [[ $j -lt $p ]]; do keys_burst_down "$s" 1; j=$((j + 1)); done
    ok "$s"
    sleep 0.5
    keys_burst_down "$s" 5
    ok "$s"
    sleep 0.4
    mute_device "$s"
    back "$s"
    back "$s"
  done

  # P3: Get Music type search intent + keyboard abandon
  adb_s "$s" shell "am start -n $ACT --ez solar_adb_get_music_type_search true" \
    >/dev/null 2>&1 || true
  sleep 2
  keys_burst_down "$s" 3
  ok "$s"
  sleep 0.5
  back "$s"
  back "$s"

  # P4: Context menu from home + settings
  adb_s "$s" shell "am start -n $ACT --ez solar_adb_open_context true --ei solar_adb_context_screen 1" \
    >/dev/null 2>&1 || true
  sleep 2
  keys_burst_down "$s" 4
  back "$s"
  adb_s "$s" shell "am start -n $ACT --ez solar_adb_open_context true --ei solar_adb_context_screen 4" \
    >/dev/null 2>&1 || true
  sleep 2
  keys_burst_down "$s" 3
  back "$s"

  # P5: Settings (4) → scroll → back
  adb_s "$s" shell "am start -n $ACT --ez solar_adb_goto true --ei solar_adb_screen 4" \
    >/dev/null 2>&1 || true
  sleep 1.2
  keys_burst_down "$s" 12
  keys_burst_up "$s" 4
  ok "$s"
  sleep 0.4
  keys_burst_down "$s" 6
  back "$s"
  back "$s"

  # P6: Podcasts (11) open/scroll
  adb_s "$s" shell "am start -n $ACT --ez solar_adb_goto true --ei solar_adb_screen 11" \
    >/dev/null 2>&1 || true
  sleep 1.2
  keys_burst_down "$s" 5
  back "$s"

  # P7: Rapid BACK spam / focus recovery
  local r=0
  while [[ $r -lt 15 ]]; do back "$s"; r=$((r + 1)); done
  adb_s "$s" shell "am start -n $ACT --ez solar_adb_goto true --ei solar_adb_screen 1" \
    >/dev/null 2>&1 || true
  sleep 1
  keys_burst_down "$s" 3
  ok "$s"
  sleep 0.5
  back "$s"

  # P8: Home menu log probe
  adb_s "$s" shell "am start -n $ACT --ez solar_adb_log_home_menu true" \
    >/dev/null 2>&1 || true
  sleep 2.5

  mute_device "$s"
  adb_s "$s" logcat -d -s SolarAdbTest:I AndroidRuntime:E 2>/dev/null \
    | tr -d '\r' > "$d/keys-logcat.txt" || true
  # Crash detection
  if grep -qE 'FATAL EXCEPTION|AndroidRuntime' "$d/keys-logcat.txt" 2>/dev/null; then
    log "    KEYS CRASH $s — see keys-logcat.txt"
    echo CRASH > "$d/keys.result"
  else
    log "    KEYS DONE $s"
    echo OK > "$d/keys.result"
  fi
}

# ---------- Execute fleet (serial devices to avoid adb saturation) ----------
OVERALL=0
for s in "${SERIALS[@]}"; do
  if [[ $KEYS_ONLY -eq 0 ]]; then
    run_matrix_on "$s" || OVERALL=1
  fi
  if [[ $MATRIX_ONLY -eq 0 ]]; then
    run_keys_on "$s" || OVERALL=1
  fi
done

# Summary
log "==> SUMMARY $RUN_ID"
for s in "${SERIALS[@]}"; do
  mr="?"; kr="?"
  [[ -f "$OUT_ROOT/$s/matrix.result" ]] && mr=$(cat "$OUT_ROOT/$s/matrix.result")
  [[ -f "$OUT_ROOT/$s/keys.result" ]] && kr=$(cat "$OUT_ROOT/$s/keys.result")
  log "    $s  matrix=$mr  keys=$kr"
  [[ "$mr" == "PASS" || "$mr" == "?" ]] || OVERALL=1
  [[ "$kr" == "OK" || "$kr" == "?" ]] || OVERALL=1
done

# Aggregate FAIL lines
{
  echo "# Failures ($RUN_ID)"
  for s in "${SERIALS[@]}"; do
    echo "## $s"
    grep ' FAIL ' "$OUT_ROOT/$s/matrix-logcat.txt" 2>/dev/null || true
    grep -E 'FATAL|AndroidRuntime' "$OUT_ROOT/$s/keys-logcat.txt" 2>/dev/null || true
  done
} > "$OUT_ROOT/failures.txt"

log "artifacts: $OUT_ROOT"
exit $OVERALL
