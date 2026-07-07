#!/usr/bin/env bash
# Install Xposed Dalvik framework on a rooted Y1/Y2 via adb + su (full /system patch).
# Pushes every file required for working zygote hook — mirrors install-xposed-system.sh.
# Usage: install-xposed-adb.sh [--no-reboot] [--api 17|19|auto] [--verify-only]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-adb-system.sh"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-xposed-install.sh"

REBOOT=1
API="auto"
VERIFY_ONLY=0
while [ $# -gt 0 ]; do
    case "$1" in
        --no-reboot) REBOOT=0; shift ;;
        --verify-only) VERIFY_ONLY=1; shift ;;
        --api) API="${2:?--api needs 17 or 19}"; shift 2 ;;
        *) echo "usage: $0 [--no-reboot] [--verify-only] [--api 17|19|auto]" >&2; exit 1 ;;
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

if [ "$VERIFY_ONLY" -eq 1 ]; then
    echo "==> Xposed verify-only (API $API)"
    # #region agent log
    xposed_agent_debug_log "H0" "install-xposed-adb.sh:verify-only" "verify-only start" "{\"api\":$API}"
    # #endregion
    xposed_verify_device_via_adb
    exit $?
fi

xposed_install_full_via_adb "$API" "$SCRIPT_DIR"

if [ "$REBOOT" -eq 1 ]; then
    echo "==> rebooting (zygote must reload app_process + XposedBridge.jar)"
    "${SOLAR_ADB[@]}" reboot
    "${SOLAR_ADB[@]}" wait-for-device
    sleep 10
    echo "==> post-reboot: re-seed runtime + verify"
    adb_system_preflight
    xposed_seed_runtime_via_adb
    xposed_verify_device_via_adb || true
    LOG="$("${SOLAR_ADB[@]}" logcat -d 2>/dev/null | grep -i XposedBridge | tail -3 || true)"
    if [ -n "$LOG" ]; then
        echo "$LOG"
    else
        echo "WARN: no XposedBridge logcat lines (MTK app_process mismatch may cause boot without Xposed)"
    fi
    ANDROID_SERIAL="${SOLAR_ADB_SERIAL:-}" ANDROID_ADB_TRANSPORT="${SOLAR_ADB_TRANSPORT:-}" \
        "$SCRIPT_DIR/audit-device-parity.sh" || true
else
    echo "Skipped reboot (--no-reboot). Reboot manually, then: $0 --verify-only --api $API"
fi

echo "==> Xposed adb install complete (API $API)"
