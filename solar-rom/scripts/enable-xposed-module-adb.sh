#!/usr/bin/env bash
# Enable an Xposed module without the Installer UI (wheel devices / automation).
# Usage: enable-xposed-module-adb.sh PACKAGE [on|off]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-adb-system.sh"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-xposed-install.sh"

PKG="${1:?usage: $0 PACKAGE [on|off]}"
MODE="${2:-on}"

adb_system_init
adb_system_preflight

APK_PATH="$("${SOLAR_ADB[@]}" shell pm path "$PKG" 2>/dev/null | tr -d '\r' | grep -m1 '^package:' | cut -d: -f2-)"
if [ -z "$APK_PATH" ]; then
    echo "enable-xposed-module: package not installed: $PKG" >&2
    exit 1
fi

if [ "$MODE" = "on" ]; then
    xposed_ensure_module_enabled_via_adb "$PKG"
else
    XPOSED_DATA="/data/data/de.robv.android.xposed.installer"
    PREFS="$XPOSED_DATA/shared_prefs/enabled_modules.xml"
    adb_su_sh "mkdir -p $XPOSED_DATA/conf $XPOSED_DATA/shared_prefs"
    adb_su_sh "sed -i '/<int name=\"$PKG\"/d' $PREFS 2>/dev/null || rm -f $PREFS"
    adb_su_sh "grep -v '$PKG' $XPOSED_DATA/conf/modules.list > $XPOSED_DATA/conf/modules.list.tmp 2>/dev/null \
        && mv $XPOSED_DATA/conf/modules.list.tmp $XPOSED_DATA/conf/modules.list \
        || echo -n > $XPOSED_DATA/conf/modules.list"
    xposed_fix_installer_data_ownership_via_adb
    echo "==> disabled $PKG"
fi

echo "Reboot for module hooks to load."
