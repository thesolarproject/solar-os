#!/usr/bin/env bash
# Ensure Xposed Dalvik framework is installed and active on connected Y1/Y2 (adb + su).
# Usage: ensure-xposed-framework-adb.sh [--no-reboot] [--api 17|19|auto]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-adb-system.sh"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-xposed-install.sh"

REBOOT=1
API="auto"
while [ $# -gt 0 ]; do
    case "$1" in
        --no-reboot) REBOOT=0; shift ;;
        --api) API="${2:?--api needs 17 or 19}"; shift 2 ;;
        *) echo "usage: $0 [--no-reboot] [--api 17|19|auto]" >&2; exit 1 ;;
    esac
done

adb_system_init
adb_system_preflight

if [ "$API" = "auto" ]; then
    SDK="$("${SOLAR_ADB[@]}" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
    case "$SDK" in
        17) API=17 ;;
        19) API=19 ;;
        *) adb_system_die "unsupported SDK $SDK — pass --api 17 or 19" ;;
    esac
fi

# Duplicate-serial guard — Y1 API 17 vs Y2 API 19 must match getprop before /system writes.
case "$API" in
    17) export SOLAR_EXPECT_MODEL=y1 ;;
    19) export SOLAR_EXPECT_MODEL=y2 ;;
esac

MODEL="$("${SOLAR_ADB[@]}" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
echo "==> ensure-xposed-framework: model=$MODEL API=$API serial=${SOLAR_ADB_SERIAL:-default}"

if xposed_app_process_active_via_adb && xposed_verify_device_via_adb; then
    echo "==> Xposed already active — enabling context bridge module only"
    BRIDGE_PKG="$(xposed_context_bridge_pkg_for_api "$API")"
    ANDROID_SERIAL="${SOLAR_ADB_SERIAL:-}" ANDROID_ADB_TRANSPORT="${SOLAR_ADB_TRANSPORT:-}" \
        "$SCRIPT_DIR/enable-xposed-module-adb.sh" "$BRIDGE_PKG" on
else
    echo "==> Full Xposed install (framework not active)"
    xposed_install_full_via_adb "$API" "$SCRIPT_DIR"
fi

if [ "$REBOOT" -eq 1 ]; then
    echo "==> rebooting to load Xposed + modules in zygote"
    "${SOLAR_ADB[@]}" reboot
    "${SOLAR_ADB[@]}" wait-for-device
    sleep 15
    adb_system_preflight
    xposed_seed_runtime_via_adb
    xposed_fix_installer_data_ownership_via_adb
    xposed_verify_device_via_adb
    ANDROID_SERIAL="${SOLAR_ADB_SERIAL:-}" ANDROID_ADB_TRANSPORT="${SOLAR_ADB_TRANSPORT:-}" \
        "$SCRIPT_DIR/audit-device-parity.sh" || true
fi

echo "==> ensure-xposed-framework complete"
