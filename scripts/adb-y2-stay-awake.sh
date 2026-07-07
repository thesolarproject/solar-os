#!/usr/bin/env bash
# Debug-only: keep Y2 screen on while testing handoff over adb (not baked into ROM).
set -euo pipefail
SERIAL="${1:-SLCAFDFA9A42}"
ADB=(adb -s "$SERIAL")
"${ADB[@]}" shell su -c 'settings put system screen_off_timeout 2147483647'
"${ADB[@]}" shell su -c 'svc power stayon usb'
"${ADB[@]}" shell input keyevent 26
echo "Y2 $SERIAL: stay-awake enabled (USB debug session only)"
