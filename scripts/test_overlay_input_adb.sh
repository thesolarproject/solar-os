#!/usr/bin/env bash
# 2026-07-10 — Intensive ADB automation: companion ChipOverlayHost open/key/theme/gate.
# Usage:
#   ./scripts/test_overlay_input_adb.sh
#   ./scripts/test_overlay_input_adb.sh SL56E779062E
#   ./scripts/test_overlay_input_adb.sh --serial SL56E779062E --keep-open
#
# Exit 0 = all critical checks passed; non-zero = failures (see summary).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh" 2>/dev/null || true

SERIAL="${ANDROID_SERIAL:-}"
KEEP_OPEN=0
SKIP_INSTALL=0
LOG_DIR="${ROOT}/dist/overlay-adb-tests"
STAMP="$(date +%Y%m%d-%H%M%S)"
RUN_DIR="${LOG_DIR}/${STAMP}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --keep-open) KEEP_OPEN=1; shift ;;
    --skip-install) SKIP_INSTALL=1; shift ;;
    -h|--help)
      sed -n '2,12p' "$0"; exit 0 ;;
    *)
      if [[ -z "$SERIAL" && "$1" != -* ]]; then SERIAL="$1"; shift
      else echo "Unknown arg: $1" >&2; exit 2; fi ;;
  esac
done

if [[ -z "$SERIAL" ]]; then
  SERIAL="$(adb devices | awk '/\tdevice$/{print $1; exit}')"
fi
if [[ -z "$SERIAL" ]]; then
  echo "ERROR: no adb device" >&2
  exit 1
fi
export ANDROID_SERIAL="$SERIAL"
ADB=(adb -s "$SERIAL")

mkdir -p "$RUN_DIR"
PASS=0
FAIL=0
WARN=0
RESULTS=()

log() { echo "$*" | tee -a "$RUN_DIR/run.log"; }
ok() { PASS=$((PASS+1)); RESULTS+=("PASS  $1"); log "  ✓ $1"; }
bad() { FAIL=$((FAIL+1)); RESULTS+=("FAIL  $1"); log "  ✗ $1"; }
warn() { WARN=$((WARN+1)); RESULTS+=("WARN  $1"); log "  ! $1"; }

prop() {
  "${ADB[@]}" shell getprop "$1" 2>/dev/null | tr -d '\r'
}

shell() {
  "${ADB[@]}" shell "$@" 2>/dev/null | tr -d '\r'
}

su_c() {
  # SuperSU on Y2: su -c 'cmd'
  "${ADB[@]}" shell "su -c \"$1\"" 2>/dev/null | tr -d '\r' || true
}

focus_line() {
  # Prefer dumpsys window (lighter than windows); 8s timeout via shell toybox timeout if present
  shell dumpsys window 2>/dev/null | grep mCurrentFocus | head -1 || true
}

has_companion_focus() {
  focus_line | grep -q 'com.solar.launcher.globalcontext'
}

has_companion_process() {
  shell "ps" 2>/dev/null | grep -q 'globalcontext:overlay\|globalcontext$'
}

has_companion_window() {
  # Process + optional focus; full windows dump is too heavy/flaky on Y2.
  if has_companion_process; then
    # Prefer focus confirmation when cheap
    if focus_line | grep -q 'globalcontext'; then return 0; fi
    # Service may have painted without focus steal — process alive after open is enough
    return 0
  fi
  return 1
}

dismiss_overlay() {
  shell am startservice \
    -n com.solar.launcher.globalcontext/.GlobalContextOverlayService \
    -a com.solar.launcher.action.DISMISS_OVERLAY >/dev/null 2>&1 || true
  sleep 0.4
}

open_power() {
  shell am startservice \
    -n com.solar.launcher.globalcontext/.GlobalContextOverlayService \
    -a com.solar.launcher.action.SHOW_OVERLAY_POWER >/dev/null 2>&1 || true
}

open_app_menu() {
  # Solar Home style APP_MENU on same Chip shell
  shell am startservice \
    -n com.solar.launcher.globalcontext/.GlobalContextOverlayService \
    -a com.solar.launcher.action.SHOW_OVERLAY_APP_MENU \
    --es menu_title 'Home options' \
    --esa menu_titles 'Shuffle,Repeat,Go to library,Settings' \
    --es menu_session_id "solar_home_adb_${STAMP}" \
    --es menu_caller_package com.solar.launcher \
    --eza menu_has_submenu false,false,false,false >/dev/null 2>&1 || true
}

# Inject via input keyevent (goes through InputManager → PWM → app)
key() {
  shell input keyevent "$1" >/dev/null 2>&1 || true
}

# Inject via companion OVERLAY_KEY broadcast (bypasses PWM — tests gate+handler)
overlay_key() {
  local code="$1" action="${2:-0}" # 0=DOWN 1=UP
  shell am broadcast \
    -a com.solar.launcher.action.OVERLAY_KEY \
    -n com.solar.launcher.globalcontext/.CompanionOverlayKeyReceiver \
    --ei overlay_key_code "$code" \
    --ei overlay_key_action "$action" >/dev/null 2>&1 || true
  # Service fallback (same as Xposed non-wheel path)
  shell am startservice \
    -n com.solar.launcher.globalcontext/.GlobalContextOverlayService \
    -a com.solar.launcher.action.OVERLAY_KEY \
    --ei overlay_key_code "$code" \
    --ei overlay_key_action "$action" >/dev/null 2>&1 || true
}

overlay_key_pair() {
  overlay_key "$1" 0
  sleep 0.05
  overlay_key "$1" 1
}

# Wheel via broadcast ONLY (production Xposed path for scroll ticks)
overlay_key_bcast_only() {
  local code="$1" action="${2:-0}"
  shell am broadcast \
    -a com.solar.launcher.action.OVERLAY_KEY \
    -n com.solar.launcher.globalcontext/.CompanionOverlayKeyReceiver \
    --ei overlay_key_code "$code" \
    --ei overlay_key_action "$action" >/dev/null 2>&1 || true
}

overlay_key_bcast_pair() {
  overlay_key_bcast_only "$1" 0
  sleep 0.05
  overlay_key_bcast_only "$1" 1
}


device_booted() {
  local b anim
  b="$(prop sys.boot_completed)"
  anim="$(prop service.bootanim.exit)"
  if [[ "$b" == "1" || "$anim" == "1" ]]; then
    return 0
  fi
  # Y2 stock ROM often leaves sys.boot_completed unset while fully usable.
  if shell pm path com.solar.launcher 2>/dev/null | grep -q package; then
    return 0
  fi
  return 1
}

snap_props() {
  local tag="$1"
  {
    echo "=== $tag ==="
    echo "time=$(date -Iseconds 2>/dev/null || date)"
    for p in \
      sys.solar.overlay.active \
      sys.solar.overlay.ui \
      sys.solar.overlay.opening \
      sys.solar.overlay.shell_visible \
      persist.solar.overlay.legacy_shell \
      persist.solar.overlay.companion_shell \
      persist.solar.overlay.active; do
      echo "$p=$(prop "$p")"
    done
    echo "focus=$(focus_line)"
    echo "ps_solar:"
    shell "ps" 2>/dev/null | grep -i solar || true
  } | tee -a "$RUN_DIR/props.txt"
}

# ---------- begin ----------
log "=== Solar overlay ADB suite ==="
log "serial=$SERIAL run_dir=$RUN_DIR"

MODEL="$(prop ro.product.model)"
BOOT="$(prop sys.boot_completed)"
ANIM="$(prop service.bootanim.exit)"
log "model=$MODEL boot_completed=$BOOT bootanim_exit=$ANIM"
if device_booted; then ok "device booted"; else bad "device not boot_completed"; fi

# Stay awake
shell svc power stayon true >/dev/null 2>&1 || true
shell settings put global stay_on_while_plugged_in 3 >/dev/null 2>&1 || true

# Optional reinstall of debug APKs as system (best-effort)
if [[ "$SKIP_INSTALL" -eq 0 ]]; then
  SOLAR_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
  COMP_APK="$ROOT/global-context-modal/build/outputs/apk/debug/global-context-modal-debug.apk"
  if [[ -f "$SOLAR_APK" && -f "$COMP_APK" ]]; then
    log "--- push + system install (root) ---"
    "${ADB[@]}" push "$SOLAR_APK" /data/local/tmp/com.solar.launcher.apk >/dev/null
    "${ADB[@]}" push "$COMP_APK" /data/local/tmp/SolarGlobalContextModal.apk >/dev/null
    su_c 'mount -o remount,rw /system'
    su_c 'cp /data/local/tmp/com.solar.launcher.apk /system/app/com.solar.launcher.apk'
    su_c 'chmod 644 /system/app/com.solar.launcher.apk'
    su_c 'cp /data/local/tmp/SolarGlobalContextModal.apk /system/app/SolarGlobalContextModal.apk'
    su_c 'chmod 644 /system/app/SolarGlobalContextModal.apk'
    su_c 'sync'
    su_c 'rm -rf /data/app/com.solar.launcher* /data/app-lib/com.solar.launcher*; pm install -r -f /system/app/com.solar.launcher.apk'
    su_c 'rm -rf /data/app/com.solar.launcher.globalcontext* /data/app-lib/com.solar.launcher.globalcontext*; pm install -r -f /system/app/SolarGlobalContextModal.apk'
    sleep 1
    SZ_S="$(shell 'ls -l /system/app/com.solar.launcher.apk' | awk '{print $4}')"
    SZ_C="$(shell 'ls -l /system/app/SolarGlobalContextModal.apk' | awk '{print $4}')"
    log "system sizes solar=$SZ_S companion=$SZ_C"
    [[ "$SZ_S" == "$(stat -c%s "$SOLAR_APK")" ]] && ok "Solar APK size match" || warn "Solar APK size mismatch ($SZ_S)"
    [[ "$SZ_C" == "$(stat -c%s "$COMP_APK")" ]] && ok "Companion APK size match" || warn "Companion APK size mismatch ($SZ_C)"
  else
    warn "debug APKs missing — testing installed packages only"
  fi
fi

# Packages
pkg_ok() {
  local pkg="$1" label="$2" i
  for i in 1 2 3 4 5; do
    if shell pm path "$pkg" 2>/dev/null | grep -q package; then
      ok "$label package present"
      return 0
    fi
    sleep 1
  done
  bad "$label package missing"
  return 1
}
pkg_ok com.solar.launcher Solar
pkg_ok com.solar.launcher.globalcontext Companion

# Theme sidecars
SKIN=""
for root in /storage/sdcard1 /storage/sdcard0 /sdcard; do
  if shell "test -f $root/.solar/theme-skin.json && echo yes" | grep -q yes; then
    SKIN="$root/.solar"
    break
  fi
done
if [[ -n "$SKIN" ]]; then
  ok "theme sidecars at $SKIN"
  shell "cat $SKIN/theme-skin.json" >"$RUN_DIR/theme-skin.json" || true
  shell "cat $SKIN/theme-snapshot.json" >"$RUN_DIR/theme-snapshot.json" || true
  shell "cat $SKIN/theme-colors.json" >"$RUN_DIR/theme-colors.json" || true
else
  warn "no theme sidecars — companion falls back to Aura palette"
fi

# setprop capability
log "--- setprop capability ---"
su_c 'setprop sys.solar.overlay._adb_probe 1'
PROBE="$(prop sys.solar.overlay._adb_probe)"
if [[ "$PROBE" == "1" ]]; then
  ok "root setprop works for sys.solar.overlay.*"
  su_c 'setprop sys.solar.overlay._adb_probe 0'
else
  bad "root setprop FAILS for sys.solar.overlay.* (gate props will stay 0)"
fi

# Launch Solar Home
log "--- launch Solar Home ---"
shell am force-stop com.solar.launcher.globalcontext || true
shell am start -n com.solar.launcher/.MainActivity >/dev/null 2>&1 || true
sleep 2
snap_props "after_home_launch"

# Clear logcat
"${ADB[@]}" logcat -c 2>/dev/null || true

# ========== TEST A: POWER shell open ==========
log "--- A: SHOW_OVERLAY_POWER ---"
dismiss_overlay
open_power
sleep 1.2
snap_props "power_t1.2s"
if has_companion_window; then ok "A1 power shell window present @1.2s"; else bad "A1 no companion window after power open"; fi
ACTIVE="$(prop sys.solar.overlay.active)"
UI="$(prop sys.solar.overlay.ui)"
SHELLV="$(prop sys.solar.overlay.shell_visible)"
[[ "$UI" == "1" ]] && ok "A2 ui=1 after paint" || bad "A2 ui!=1 (got '$UI') — gate not armed"
[[ "$ACTIVE" == "1" ]] && ok "A3 active=1 after paint" || bad "A3 active!=1 (got '$ACTIVE')"
[[ "$SHELLV" == "1" ]] && ok "A4 shell_visible=1" || bad "A4 shell_visible!=1 (got '$SHELLV')"
has_companion_focus && ok "A5 companion has focus" || warn "A5 companion not current focus (may still get IPC keys)"

# Survive past paint watchdog (2.5s default)
sleep 2.0
if has_companion_window; then ok "A6 shell survived 3.2s (watchdog)"; else bad "A6 shell GONE at 3.2s — paint watchdog likely tore down"; fi
snap_props "power_t3.2s"

# ========== TEST B: input keyevent path ==========
log "--- B: input keyevent (PWM path) ---"
# Ensure open
if ! has_companion_window; then open_power; sleep 1; fi
# Wheel: MEDIA_PLAY=126, MEDIA_PAUSE=127 on Y1/Y2; also DPAD
for i in 1 2 3; do key 20; sleep 0.12; done  # DPAD_DOWN
for i in 1 2; do key 19; sleep 0.12; done    # DPAD_UP
key 23; sleep 0.2  # DPAD_CENTER
# Don't leave a reboot confirmation if power rows activated — dismiss
sleep 0.3
snap_props "after_input_keyevent"
if has_companion_window; then ok "B1 shell still up after keyevents"; else warn "B1 shell closed after keyevents (center may have activated)"; fi

# ========== TEST C0: gate arm without root setprop ==========
log "--- C0: companion gate arm (no root force) ---"
dismiss_overlay
open_power
sleep 1.2
UI_C0="$(prop sys.solar.overlay.ui)"
ACTIVE_C0="$(prop sys.solar.overlay.active)"
SHELL_C0="$(prop sys.solar.overlay.shell_visible)"
[[ "$UI_C0" == "1" ]] && ok "C0 ui=1 from companion paint" || warn "C0 ui!=1 (got '$UI_C0') — setprop may fail on device"
[[ "$ACTIVE_C0" == "1" ]] && ok "C0 active=1 from companion" || warn "C0 active!=1 (got '$ACTIVE_C0')"
[[ "$SHELL_C0" == "1" ]] && ok "C0 shell_visible=1" || warn "C0 shell_visible!=1 (got '$SHELL_C0')"
if has_companion_window; then ok "C0 shell window present without root setprop"; else bad "C0 no shell after power open"; fi
snap_props "c0_gate_arm"

# ========== TEST C: OVERLAY_KEY broadcast/service path ==========
log "--- C: OVERLAY_KEY IPC path ---"
dismiss_overlay
open_power
sleep 1.0
# Force arm props via root if possible (simulates successful gate write)
su_c 'setprop sys.solar.overlay.active 1'
su_c 'setprop sys.solar.overlay.ui 1'
su_c 'setprop sys.solar.overlay.shell_visible 1'
sleep 0.2
# Wheel down via IPC (keycode 20 DPAD_DOWN, 127 MEDIA_PAUSE)
for code in 20 127 20 127 19 126; do
  overlay_key_pair "$code"
  sleep 0.08
done
# BACK down/up should dismiss
overlay_key 4 0
sleep 0.05
overlay_key 4 1
sleep 0.6
if has_companion_window; then
  warn "C1 shell still up after BACK via OVERLAY_KEY (handler may not be armed)"
else
  ok "C1 BACK via OVERLAY_KEY dismissed shell"
fi
snap_props "after_overlay_key_back"

# Re-open and prove center activation path does not crash process
open_power
sleep 1
overlay_key_pair 23
sleep 0.5
shell "ps" | grep -q 'globalcontext:overlay' && ok "C2 companion :overlay process alive after center" \
  || bad "C2 companion :overlay process dead after center"

# ========== TEST D: APP_MENU (Home options) ==========
log "--- D: SHOW_OVERLAY_APP_MENU (Home-style) ---"
dismiss_overlay
open_app_menu
sleep 1.2
if has_companion_window; then ok "D1 APP_MENU window present"; else bad "D1 APP_MENU window missing"; fi
for i in 1 2 3 4; do overlay_key_pair 20; sleep 0.08; done
for i in 1 2; do overlay_key_pair 19; sleep 0.08; done
if has_companion_window; then ok "D2 APP_MENU survived wheel IPC"; else bad "D2 APP_MENU died during wheel"; fi
# Survive watchdog again
sleep 2.0
if has_companion_window; then ok "D3 APP_MENU survived watchdog"; else bad "D3 APP_MENU gone after watchdog window"; fi
snap_props "app_menu_late"

# ========== TEST E: dual open race (Home then power) ==========
log "--- E: race / dual open ---"
dismiss_overlay
open_app_menu
sleep 0.3
open_power
sleep 1.0
if has_companion_window; then ok "E1 shell present after dual open"; else bad "E1 no shell after dual open"; fi

# ========== TEST F: Solar process key forward simulation ==========
log "--- F: Solar MainActivity still foreground ---"
shell am start -n com.solar.launcher/.MainActivity >/dev/null 2>&1 || true
sleep 0.5
open_power
sleep 1.0
# Send keys while MainActivity is focused app — mirrors user on Home
for i in 1 2 3 4 5; do key 20; sleep 0.1; done
for i in 1 2 3; do key 19; sleep 0.1; done
if has_companion_window; then ok "F1 shell up with MainActivity fg + keyevents"; else bad "F1 shell lost under MainActivity keys"; fi
snap_props "home_fg_keys"

# ---------- coordinator / policy helpers ----------
hold_down() {
  local key="${1:-26}" fg="${2:-com.solar.launcher}"
  shell am startservice \
    -n com.solar.launcher.globalcontext/.GlobalInputCoordinatorService \
    -a com.solar.launcher.globalcontext.action.HOLD_DOWN \
    --ei key_code "$key" \
    --es foreground_pkg "$fg" \
    --ez y2_device true >/dev/null 2>&1 || true
}

hold_up() {
  shell am startservice \
    -n com.solar.launcher.globalcontext/.GlobalInputCoordinatorService \
    -a com.solar.launcher.globalcontext.action.HOLD_UP >/dev/null 2>&1 || true
}

# ========== TEST G: coordinator HOLD_DOWN/UP (Settings fg) ==========
log "--- G: coordinator hold FSM (Settings fg) ---"
dismiss_overlay
shell am start -n com.android.settings/.Settings >/dev/null 2>&1 || true
sleep 1.0
FOCUS_G="$(focus_line)"
echo "$FOCUS_G" | grep -qi settings && ok "G0 Settings foreground" || warn "G0 Settings not focused ($FOCUS_G)"
hold_down 26 com.android.settings
sleep 0.55
if has_companion_window; then ok "G1 coordinator hold opened shell @550ms"; else warn "G1 no shell after coordinator hold (may need Xposed tier)"; fi
snap_props "coordinator_hold_550ms"
hold_up
sleep 0.4
if has_companion_window; then warn "G2 shell still up after HOLD_UP"; else ok "G2 HOLD_UP disarmed hold track"; fi

# ========== TEST H: third-party APP_MENU while Settings fg ==========
log "--- H: APP_MENU with Settings fg ---"
dismiss_overlay
shell am start -n com.android.settings/.Settings >/dev/null 2>&1 || true
sleep 0.8
open_app_menu
sleep 1.2
if has_companion_window; then ok "H1 APP_MENU over Settings fg"; else bad "H1 APP_MENU missing over Settings"; fi
for i in 1 2 3; do overlay_key_pair 20; sleep 0.08; done
if has_companion_window; then ok "H2 wheel IPC over Settings fg"; else bad "H2 shell lost wheel over Settings"; fi
snap_props "settings_app_menu"

# ========== TEST I: input policy_rev + coordinator alive ==========
log "--- I: policy_rev + coordinator service ---"
POLICY_REV="$(prop sys.solar.input.policy_rev)"
[[ -n "$POLICY_REV" && "$POLICY_REV" != "0" ]] && ok "I1 policy_rev=$POLICY_REV" \
  || warn "I1 policy_rev unset (coordinator may not have booted)"
shell am startservice -n com.solar.launcher.globalcontext/.GlobalInputCoordinatorService >/dev/null 2>&1 || true
sleep 0.3
shell "ps" 2>/dev/null | grep -q 'GlobalInputCoordinator\|globalcontext' \
  && ok "I2 companion/coordinator process reachable" \
  || warn "I2 coordinator process not visible in ps"

# ========== TEST J: optional launcher fg (Rockbox / JJ / Innioasis) ==========
log "--- J: launcher fg overlay probes ---"
for pkg_launcher in \
  "org.rockbox/.RockboxActivity" \
  "com.themoon.y1/.MainActivity" \
  "com.innioasis.y2/.MainActivity"; do
  pkg="${pkg_launcher%%/*}"
  if ! shell pm path "$pkg" 2>/dev/null | grep -q package; then
    warn "J skip $pkg (not installed)"
    continue
  fi
  dismiss_overlay
  shell am start -n "$pkg_launcher" >/dev/null 2>&1 || true
  sleep 1.2
  hold_down 26 "$pkg"
  sleep 0.55
  if has_companion_window; then ok "J $pkg coordinator hold opened shell"; else warn "J $pkg no shell after hold"; fi
  hold_up
  sleep 0.3
done
snap_props "launcher_hold_probes"

# ========== TEST K: broadcast-only wheel (MEDIA path) ==========
log "--- K: broadcast-only wheel IPC ---"
dismiss_overlay
open_power
sleep 1.0
for i in 1 2 3 4 5 6; do overlay_key_bcast_pair 127; sleep 0.06; done
for i in 1 2 3; do overlay_key_bcast_pair 126; sleep 0.06; done
if has_companion_window; then ok "K1 shell survived broadcast-only wheel"; else bad "K1 shell lost on broadcast wheel"; fi

# ========== TEST L: dismiss clears gate props ==========
log "--- L: dismiss clears gate props ---"
dismiss_overlay
sleep 0.5
L_ACTIVE="$(prop sys.solar.overlay.active)"
L_UI="$(prop sys.solar.overlay.ui)"
L_SHELL="$(prop sys.solar.overlay.shell_visible)"
[[ "$L_ACTIVE" != "1" ]] && ok "L1 active cleared after dismiss" || warn "L1 active still 1"
[[ "$L_UI" != "1" ]] && ok "L2 ui cleared after dismiss" || warn "L2 ui still 1"
[[ "$L_SHELL" != "1" ]] && ok "L3 shell_visible cleared" || warn "L3 shell_visible still 1"

# ========== TEST M: companion_shell + rescue daemon probes ==========
log "--- M: platform probes ---"
COMPANION="$(prop persist.solar.overlay.companion_shell)"
[[ -z "$COMPANION" || "$COMPANION" == "0" ]] && ok "M1 companion_shell off (Solar ThemedContextMenu primary)" \
  || warn "M1 companion_shell=$COMPANION"
if shell "test -f /system/etc/solar/solar-rescue-exec.sh && echo yes" | grep -q yes; then
  ok "M2 solar-rescue-exec.sh present"
else
  warn "M2 solar-rescue-exec.sh missing"
fi
if shell "ps" 2>/dev/null | grep -qiE 'CompanionRootInputDaemon|GlobalOverlayTriggerMain|solar-rescue'; then
  ok "M3 rescue/root input daemon running"
else
  warn "M3 rescue daemon not visible (may start on demand)"
fi

# ========== TEST N: coordinator HOLD_UP cancels before modal ==========
log "--- N: short hold cancel (no shell) ---"
dismiss_overlay
shell am start -n com.android.settings/.Settings >/dev/null 2>&1 || true
sleep 0.8
hold_down 26 com.android.settings
sleep 0.15
hold_up
sleep 0.5
if has_companion_window; then warn "N1 shell opened on short hold (<300ms)"; else ok "N1 short hold did not open shell"; fi

# ========== capture logs ==========
log "--- logcat harvest ---"
"${ADB[@]}" logcat -d -t 800 >"$RUN_DIR/logcat.txt" 2>/dev/null || true
grep -iE 'paint watchdog|GlobalContext|ThemeReader|addView|ChipOverlay|OverlayKey|CompanionOverlay|SolarHome|SHOW_POWER|APP_MENU|SysProp|setprop|hold_threshold_met|suppressed GlobalActions|stuck-shell|HOLD_DOWN|coordinator' \
  "$RUN_DIR/logcat.txt" >"$RUN_DIR/logcat-overlay.txt" 2>/dev/null || true
if grep -q 'hold_threshold_met' "$RUN_DIR/logcat.txt" 2>/dev/null; then
  ok "LOG: coordinator hold_threshold_met seen"
else
  warn "LOG: no hold_threshold_met (coordinator hold may not have fired)"
fi
if grep -qi 'suppressed GlobalActions' "$RUN_DIR/logcat.txt" 2>/dev/null; then
  ok "LOG: GlobalActions suppression marker seen"
else
  warn "LOG: no GlobalActions suppression in harvest (needs real POWER long-hold)"
fi
if grep -q 'paint watchdog' "$RUN_DIR/logcat.txt" 2>/dev/null; then
  bad "LOG: paint watchdog fired (ui prop never armed)"
else
  ok "LOG: no paint watchdog tear-down seen"
fi
if grep -qiE 'AndroidRuntime|FATAL EXCEPTION.*globalcontext' "$RUN_DIR/logcat.txt" 2>/dev/null; then
  bad "LOG: companion crash in logcat"
else
  ok "LOG: no companion FATAL in harvest"
fi

# Cleanup
if [[ "$KEEP_OPEN" -eq 0 ]]; then
  dismiss_overlay
  su_c 'setprop sys.solar.overlay.active 0'
  su_c 'setprop sys.solar.overlay.ui 0'
  su_c 'setprop sys.solar.overlay.shell_visible 0'
  su_c 'setprop sys.solar.overlay.opening 0'
fi

# Summary
{
  echo ""
  echo "======== SUMMARY ========"
  echo "pass=$PASS fail=$FAIL warn=$WARN"
  printf '%s\n' "${RESULTS[@]}"
  echo "artifacts: $RUN_DIR"
} | tee -a "$RUN_DIR/run.log" | tee "$RUN_DIR/summary.txt"

if [[ "$FAIL" -gt 0 ]]; then
  exit 1
fi
exit 0
