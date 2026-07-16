#!/usr/bin/env bash
# 2026-07-15 — Classify adb serials as y1 | y2 | a5 without trusting ro.product.model alone.
# 2026-07-16 — Match DeviceFeatures: A5 panel → SoC → pin → 360p via SDK (19=y2, ≤17=y1).
# Layman: A5 ROMs often say "Y1" but the screen is 240×320; Y2 shares 360p with Y1 (use SDK/SoC).
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
looks_360() {
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

read_cpu_hardware() {
  local s="$1"
  "$ADB" -s "$s" shell "cat /proc/cpuinfo 2>/dev/null | grep -i '^Hardware' | head -1" 2>/dev/null \
    | tr -d '\r' | sed 's/.*:[[:space:]]*//' | tr '[:upper:]' '[:lower:]'
}

classify_serial() {
  local s="$1"
  local prop model brand wh w h sdk cpu board
  wh="$(read_display_wh "$s")"
  w="${wh%% *}"
  h="${wh##* }"
  # 1) A5 QVGA is unique — force a5 even when model/pin say y1.
  if looks_a5 "$w" "$h"; then
    echo "a5"
    return 0
  fi
  model="$("$ADB" -s "$s" shell getprop ro.product.model 2>/dev/null | tr -d '\r' | tr '[:upper:]' '[:lower:]')"
  brand="$("$ADB" -s "$s" shell getprop ro.product.brand 2>/dev/null | tr -d '\r' | tr '[:upper:]' '[:lower:]')"
  if [[ "$model" == *a5* || "$brand" == *a5* ]]; then
    echo "a5"
    return 0
  fi
  # 2) SoC before shared 360p panel (Y2 = MT6582, Y1 = MT6572).
  cpu="$(read_cpu_hardware "$s")"
  board="$("$ADB" -s "$s" shell getprop ro.product.board 2>/dev/null | tr -d '\r' | tr '[:upper:]' '[:lower:]')"
  if [[ "$cpu" == *mt6582* || "$board" == *mt6582* ]]; then
    echo "y2"
    return 0
  fi
  if [[ "$cpu" == *mt6572* || "$board" == *mt6572* ]]; then
    echo "y1"
    return 0
  fi
  # 3) ROM pin after SoC / A5 (lab + Solar images).
  prop="$("$ADB" -s "$s" shell getprop persist.solar.device_family 2>/dev/null | tr -d '\r' | tr '[:upper:]' '[:lower:]')"
  if [[ "$prop" == "a5" || "$prop" == "y1" || "$prop" == "y2" ]]; then
    echo "$prop"
    return 0
  fi
  # 4) Shared ~360×480: SDK 19+ → y2 (4.4.x), SDK ≤17 → y1 (4.2.x).
  sdk="$("$ADB" -s "$s" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
  case "$sdk" in ''|*[!0-9]*) sdk=0 ;; esac
  if looks_360 "$w" "$h"; then
    if [[ "$sdk" -ge 19 ]]; then
      echo "y2"
      return 0
    fi
    if [[ "$sdk" -le 17 ]]; then
      echo "y1"
      return 0
    fi
    if [[ "$model" == *y2* ]]; then
      echo "y2"
      return 0
    fi
    if [[ "$model" == *y1* ]]; then
      echo "y1"
      return 0
    fi
    echo "y1"
    return 0
  fi
  # 5) Fallbacks without reliable panel.
  if [[ "$sdk" -ge 19 ]]; then
    echo "y2"
    return 0
  fi
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
  local fam model wh sdk
  fam="$(classify_serial "$s")"
  model="$("$ADB" -s "$s" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
  wh="$(read_display_wh "$s")"
  sdk="$("$ADB" -s "$s" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
  printf '%s\tfamily=%s\tmodel=%s\tdisplay=%s\tsdk=%s\n' "$s" "$fam" "$model" "${wh// /x}" "$sdk"
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
