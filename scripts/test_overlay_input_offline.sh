#!/usr/bin/env bash
# 2026-07-10 — Run overlay input unit tests when no Y2 device is connected.
# Usage: ./scripts/test_overlay_input_offline.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "=== Solar overlay offline test suite ==="

if adb devices 2>/dev/null | awk '/\tdevice$/{exit 0} END{exit 1}'; then
  echo "Device connected — prefer: ./scripts/test_overlay_input_adb.sh"
  echo "Continuing with unit tests only."
else
  echo "No adb device — unit tests only."
fi

echo "--- global-context-modal unit tests ---"
./gradlew :global-context-modal:testDebugUnitTest

echo "--- app overlay/policy unit tests ---"
./gradlew :app:testDebugUnitTest \
  --tests 'com.solar.launcher.OverlayKeyGateTest' \
  --tests 'com.solar.launcher.StaleOverlayGateTest' \
  --tests 'com.solar.launcher.SolarOverlayFocusTest' \
  --tests 'com.solar.input.policy.GlobalInputPolicyTest'

echo "--- script syntax check ---"
bash -n "$ROOT/scripts/test_overlay_input_adb.sh"
bash -n "$ROOT/scripts/verify-overlay-holds.sh"

echo "=== offline suite passed ==="
