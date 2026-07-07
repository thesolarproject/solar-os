#!/usr/bin/env bash
# 2026-07-06 — adb smoke: JJ wheel handoff sysprops + logcat inject lines.
# Layman: confirms Solar is translating the wheel while JJ Launcher is on screen.
# Usage: ./scripts/verify-jj-input.sh [adb-serial]

set -euo pipefail
ADB=(adb)
if [[ -n "${1:-}" ]]; then
  ADB=(adb -s "$1")
fi

echo "=== JJ input handoff verify ==="
"${ADB[@]}" wait-for-device

FG="$("${ADB[@]}" shell dumpsys activity activities 2>/dev/null | grep mResumedActivity | head -1 || true)"
echo "resumed: ${FG:-unknown}"

echo "--- sysprops ---"
for p in sys.solar.handoff.active sys.solar.handoff.jj persist.solar.home.target; do
  v="$("${ADB[@]}" shell getprop "$p" 2>/dev/null | tr -d '\r')"
  echo "$p=$v"
done

echo "--- dpad mode (logcat snapshot, 3s) ---"
"${ADB[@]}" logcat -c 2>/dev/null || true
echo "Wheel in JJ for 3s now..."
sleep 3
"${ADB[@]}" logcat -d -t 80 2>/dev/null | grep -E 'ExternalInputHandoff|MediaButtonRegistrar|RockboxForegroundMonitor|inject|MODE_JJ|dpadMode' || echo "(no handoff log lines — enable debug or wheel JJ)"

echo "=== done ==="
