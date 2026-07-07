#!/usr/bin/env bash
# Re-enable Solar context bridge after pm install -r (fixes stale modules.list + auto-disable).
# Usage: sync-context-bridge-module-adb.sh [--reboot]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-adb-system.sh"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-xposed-install.sh"

REBOOT=0
if [ "${1:-}" = "--reboot" ]; then
    REBOOT=1
fi

adb_system_init
adb_system_preflight

API="$("${SOLAR_ADB[@]}" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
PKG="$(xposed_context_bridge_pkg_for_api "${API:-19}")"

echo "==> sync context bridge module ($PKG, API ${API:-?})"
xposed_ensure_module_enabled_via_adb "$PKG"
adb_su_sh "[ -x $XPOSED_SYSTEM_INIT_HOOK ] && sh $XPOSED_SYSTEM_INIT_HOOK || true"
xposed_fix_installer_data_ownership_via_adb

echo "==> state after sync"
"${SOLAR_ADB[@]}" shell su -c "grep '$PKG' /data/data/de.robv.android.xposed.installer/shared_prefs/enabled_modules.xml 2>/dev/null" | tr -d '\r' || true
"${SOLAR_ADB[@]}" shell su -c "grep '$PKG' /data/data/de.robv.android.xposed.installer/conf/modules.list 2>/dev/null" | tr -d '\r' || true

if [ "$REBOOT" = "1" ]; then
    echo "==> reboot"
    "${SOLAR_ADB[@]}" reboot
fi
