#!/usr/bin/env bash
# Pull a working Xposed install from a connected device into solar-rom/vendor/xposed/.
# Run after successful install-xposed-adb.sh on hardware-verified Y1/Y2.
# Usage: extract-xposed-vendor.sh [--api 17|19|auto] [adb_serial]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENDOR="$SCRIPT_DIR/../vendor/xposed"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-adb-system.sh"

API="auto"
SERIAL="${1:-${ANDROID_SERIAL:-}}"
if [ "${1:-}" = "--api" ]; then
    API="${2:?--api needs 17 or 19}"
    SERIAL="${3:-${ANDROID_SERIAL:-}}"
fi

adb_system_init
[ -n "$SERIAL" ] && SOLAR_ADB=(adb -s "$SERIAL")
"${SOLAR_ADB[@]}" get-state >/dev/null 2>&1 || { echo "error: no adb device" >&2; exit 1; }

if [ "$API" = "auto" ]; then
    SDK="$("${SOLAR_ADB[@]}" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
    case "$SDK" in
        17) API=17 ;;
        19) API=19 ;;
        *) echo "error: unsupported SDK $SDK" >&2; exit 1 ;;
    esac
fi

DEST="$VENDOR/api${API}-arm"
mkdir -p "$DEST"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

pull_system() {
    local remote="$1" local_path="$2"
    "${SOLAR_ADB[@]}" pull "$remote" "$local_path" >/dev/null 2>&1 \
        || { echo "error: pull $remote failed" >&2; exit 1; }
}

echo "==> extract-xposed-vendor API $API -> $DEST"
pull_system /system/bin/app_process "$DEST/app_process"
pull_system /system/framework/XposedBridge.jar "$DEST/XposedBridge.jar"
pull_system /system/xposed.prop "$DEST/xposed.prop" 2>/dev/null || {
    cat > "$DEST/xposed.prop" <<EOF
version=58
arch=arm
sdk=${API}
dalvik=1
extracted=$(date -u +%Y-%m-%dT%H:%MZ)
EOF
}

if "${SOLAR_ADB[@]}" shell "[ -f /system/app/XposedInstaller.apk ] && echo yes" 2>/dev/null | grep -q yes; then
    pull_system /system/app/XposedInstaller.apk "$VENDOR/XposedInstaller.apk"
fi

MODEL="$("${SOLAR_ADB[@]}" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
cat > "$DEST/VENDORED.txt" <<EOF
Extracted from live device ($MODEL, API $API)
Date: $(date -u +%Y-%m-%dT%H:%MZ)
Regenerate: solar-rom/scripts/extract-xposed-vendor.sh --api $API
EOF

echo "==> vendor updated: $DEST"
ls -la "$DEST"
