#!/usr/bin/env bash
# 2026-07-15 — Classify adb serials as y1 | y2 | a5 without trusting ro.product.model alone.
# Layman: A5 ROMs often say "Y1" but the screen is 240×320 portrait with a different keypad.
# Tech: prefer dumpsys display Built-in size; then persist.solar.device_family; then model tokens.
# Usage: ./scripts/detect-device-family-adb.sh [serial]
#        ./scripts/detect-device-family-adb.sh --all
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
[[ -f "$ROOT/scripts/env.sh" ]] && source "$ROOT/scripts/env.sh" || true
ADB="${ADB:-adb}"

# Pure size gate (keep in sync with DeviceFeatures.looksLikeA5Display / looksLikeY1Display).
looks_a5() {
  local w="$1" h="$2"
  [[ -z "$w" || -z "$h" || "$w" -le 0 || "$h" -le 0 ]] && return 1
  local a b
  if [[ "$w" -lt "$h" ]]; then a=$w; b=$h; else a=$h; b=$w; fi
  [[ "$a" -ge 220 && "$a" -le 260 && "$b" -ge 300 && "$b" -le 340 ]]
}
looks_y1() {
  local w="$1" h="$2"
  [[ -z "$w" || -z "$h" || "$w" -le 0 || "$h" -le 0 ]] && return 1
  local a b
  if [[ "$w" -lt "$h" ]]; then a=$w; b=$h; else a=$h; b=$w; fi
  [[ "$a" -ge 340 && "$a" -le 380 && "$b" -ge 460 && "$b" -le 500 ]]
}

read_display_wh() {
  local s="$1"
  local line w h
  # Prefer Built-in Screen line: "480 x 360"
  line="$("$ADB" -s "$s" shell dumpsys display 2>/dev/null | tr -d '\r' \
    | grep -E 'Built-in Screen|logicalFrame=Rect' | head -1 || true)"
  if [[ "$line" =~ ([0-9]+)[[:space:]]*x[[:space:]]*([0-9]+) ]]; then
    echo "${BASH_REMATCH[1]} ${BASH_REMATCH[2]}"
    return 0
  fi
  if [[ "$line" =~ Rect\(0,\ 0\ -\ ([0-9]+),\ ([0-9]+)\) ]]; then
    echo "${BASH_REMATCH[1]} ${BASH_REMATCH[2]}"
    return 0
  fi
  # Avoid getevent -pl here — it can hang on some MTK builds; dumpsys is enough.
  echo "0 0"
}

classify_serial() {
  local s="$1"
  local prop model brand wh w h
  prop="$("$ADB" -s "$s" shell getprop persist.solar.device_family 2>/dev/null | tr -d '\r' | tr '[:upper:]' '[:lower:]')"
  if [[ "$prop" == "a5" || "$prop" == "y1" || "$prop" == "y2" ]]; then
    echo "$prop"
    return 0
  fi
  wh="$(read_display_wh "$s")"
  w="${wh%% *}"
  h="${wh##* }"
  if looks_a5 "$w" "$h"; then
    echo "a5"
    return 0
  fi
  if looks_y1 "$w" "$h"; then
    echo "y1"
    return 0
  fi
  model="$("$ADB" -s "$s" shell getprop ro.product.model 2>/dev/null | tr -d '\r' | tr '[:upper:]' '[:lower:]')"
  brand="$("$ADB" -s "$s" shell getprop ro.product.brand 2>/dev/null | tr -d '\r' | tr '[:upper:]' '[:lower:]')"
  if [[ "$model" == *a5* || "$brand" == *a5* ]]; then
    echo "a5"
    return 0
  fi
  # Do NOT map bare model=y1 → y1 only; without display we already failed size gates.
  if [[ "$model" == *y2* ]]; then
    echo "y2"
    return 0
  fi
  if [[ "$model" == *y1* ]]; then
    echo "y1"
    return 0
  fi
  echo "unknown"
}

describe_serial() {
  local s="$1"
  local fam model wh
  fam="$(classify_serial "$s")"
  model="$("$ADB" -s "$s" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
  wh="$(read_display_wh "$s")"
  printf '%s\tfamily=%s\tmodel=%s\tdisplay=%s\n' "$s" "$fam" "$model" "${wh// /x}"
}

if [[ "${1:-}" == "--all" || -z "${1:-}" ]]; then
  mapfile -t serials < <("$ADB" devices | awk '/\tdevice$/{print $1}')
  if [[ ${#serials[@]} -eq 0 ]]; then
    echo "no adb devices" >&2
    exit 1
  fi
  for s in "${serials[@]}"; do
    describe_serial "$s"
  done
else
  describe_serial "$1"
fi
