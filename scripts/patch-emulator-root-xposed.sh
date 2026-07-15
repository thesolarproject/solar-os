#!/usr/bin/env bash
# 2026-07-11 — Best-effort root + Xposed ladder for API 17 x86 AVDs (fail-open).
# Layman: try to give the emulator su + Xposed so overlays match real devices; skip if locked.
# Tech: remount /system rw → push x86 su → optional install-xposed-adb; never blocks A5 UI work.
# Usage: ./scripts/patch-emulator-root-xposed.sh [serial]
# Reversal: wipe AVD data / recreate AVD; no host-side permanent change.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
[[ -f "$ROOT/scripts/env.sh" ]] && source "$ROOT/scripts/env.sh" || true
ADB="${ADB:-adb}"
SERIAL="${1:-}"
ADB_ARGS=()
[[ -n "$SERIAL" ]] && ADB_ARGS=(-s "$SERIAL")

echo "== Emulator root + Xposed (fail-open) =="
if ! "$ADB" "${ADB_ARGS[@]}" get-state >/dev/null 2>&1; then
  echo "No device/emulator. Start ./scripts/run-a5-emulator.sh first." >&2
  exit 0
fi

# Prefer Google API / eng images where adbd can restart as root.
"$ADB" "${ADB_ARGS[@]}" root >/dev/null 2>&1 || true
sleep 1
"$ADB" "${ADB_ARGS[@]}" wait-for-device >/dev/null 2>&1 || true

REMOUNT_OK=0
if "$ADB" "${ADB_ARGS[@]}" remount >/dev/null 2>&1; then
  REMOUNT_OK=1
elif "$ADB" "${ADB_ARGS[@]}" shell mount -o remount,rw /system >/dev/null 2>&1; then
  REMOUNT_OK=1
fi

if [[ "$REMOUNT_OK" -ne 1 ]]; then
  echo "WARN: /system remount blocked — keeping in-app volume + overlay only (no su/Xposed)."
  echo "DONE (fail-open). UI parity still works without this ladder."
  exit 0
fi

# Look for an x86 setuid su binary if the ROM vendor tree shipped one.
SU_CANDIDATES=(
  "$ROOT/solar-rom/vendor/su/x86/su"
  "$ROOT/solar-rom/vendor/supersu/x86/su"
  "$ROOT/vendor/su/x86/su"
)
SU_BIN=""
for c in "${SU_CANDIDATES[@]}"; do
  if [[ -f "$c" ]]; then SU_BIN="$c"; break; fi
done

if [[ -n "$SU_BIN" ]]; then
  echo "Pushing su from $SU_BIN …"
  "$ADB" "${ADB_ARGS[@]}" push "$SU_BIN" /system/xbin/su >/dev/null || true
  "$ADB" "${ADB_ARGS[@]}" shell chmod 6755 /system/xbin/su >/dev/null 2>&1 || true
  "$ADB" "${ADB_ARGS[@]}" shell chown root:root /system/xbin/su >/dev/null 2>&1 || true
else
  echo "No x86 su binary in vendor tree — skipping su stage (adbd root may still work)."
fi

XPOSED_SCRIPT="$ROOT/solar-rom/scripts/install-xposed-adb.sh"
if [[ -x "$XPOSED_SCRIPT" || -f "$XPOSED_SCRIPT" ]]; then
  echo "Attempting Xposed install (api17)…"
  # Pass serial if the script supports it; otherwise rely on sole emulator.
  if [[ -n "$SERIAL" ]]; then
    SERIAL="$SERIAL" bash "$XPOSED_SCRIPT" || echo "WARN: Xposed install failed — continue without modules."
  else
    bash "$XPOSED_SCRIPT" || echo "WARN: Xposed install failed — continue without modules."
  fi
else
  echo "install-xposed-adb.sh missing — skip Xposed stage."
fi

echo "DONE. Reboot once if su/Xposed landed: adb reboot"
echo "If remount/Xposed failed, Solar still has in-app overlay + volume HUD."
