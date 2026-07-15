#!/usr/bin/env bash
# 2026-07-06 — adb checklist: overlay hold tiers + policy revision on device.
# Usage: ./scripts/verify-overlay-holds.sh [serial]

set -euo pipefail
ADB=(adb)
if [[ -n "${1:-}" ]]; then
  ADB=(adb -s "$1")
fi

echo "=== overlay hold policy verify ==="
"${ADB[@]}" wait-for-device
for p in sys.solar.input.policy_rev sys.solar.overlay.active sys.solar.overlay.opening; do
  v="$("${ADB[@]}" shell getprop "$p" 2>/dev/null | tr -d '\r')"
  echo "$p=$v"
done
echo "Expected policy_rev=21; modal 420/300; RESCUE_EXECUTE=10000ms HUD_ARM=7000ms"
echo "Manual: BACK in Settings/SystemUI ~0.42s → modal; JJ ~0.3s → modal; Rockbox BACK → no modal"
echo "=== done ==="
