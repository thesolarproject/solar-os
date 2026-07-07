#!/usr/bin/env bash
# Build + install + enable Solar PowerMenu test module on Y2 (or ANDROID_ADB_TRANSPORT device).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-adb-system.sh"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-xposed-install.sh"

PKG="com.solar.launcher.xposed.powermenu"
APK="$SCRIPT_DIR/../vendor/xposed/solar-powermenu-test/PowerMenuTest.apk"

adb_system_init
adb_system_preflight

"$SCRIPT_DIR/build-powermenu-test-apk.sh"

echo "==> uninstall old $PKG (signature rotation)"
adb_su_sh "pm uninstall $PKG" 2>/dev/null || true

echo "==> install $APK"
"${SOLAR_ADB[@]}" install "$APK" | tr -d '\r'

APK_PATH="$("${SOLAR_ADB[@]}" shell "pm path $PKG" 2>/dev/null | tr -d '\r' | grep '^package:' | sed 's/^package://' | head -1)"
[ -n "$APK_PATH" ] || { echo "deploy-powermenu: pm path empty for $PKG" >&2; exit 1; }
echo "  module apk: $APK_PATH"

APP_UID="$(xposed_installer_app_uid_via_adb)"
[ -n "$APP_UID" ] || { echo "deploy-powermenu: could not resolve Xposed Installer uid" >&2; exit 1; }

adb_su_sh "mkdir -p /data/data/de.robv.android.xposed.installer/conf /data/data/de.robv.android.xposed.installer/shared_prefs"
# Append without clobbering other Solar modules — safe merge preserves Installer XML shape.
adb_su_sh "grep -qxF '$APK_PATH' /data/data/de.robv.android.xposed.installer/conf/modules.list 2>/dev/null || echo '$APK_PATH' >> /data/data/de.robv.android.xposed.installer/conf/modules.list"
xposed_ensure_module_enabled_via_adb "$PKG"

echo "==> reboot (Xposed loads modules at zygote start)"
"${SOLAR_ADB[@]}" reboot
"${SOLAR_ADB[@]}" wait-for-device
sleep 15

echo "==> hook evidence (expect hookAllMethods OK + total hooks > 0)"
"${SOLAR_ADB[@]}" logcat -d 2>/dev/null | grep SolarPMTest | tail -20 || true
echo "==> deploy complete — hold lock/power on Y2; run: adb logcat -s SolarPMTest:V"
