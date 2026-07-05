#!/usr/bin/env bash
# Post-zip audit: Xposed Dalvik files must be present in system.img.
# Usage: verify-xposed-rom-contents.sh [rom.zip] [api_level]
set -euo pipefail

ZIP="${1:?usage: verify-xposed-rom-contents.sh ROM.zip [17|19]}"
API="${2:-auto}"

die() { echo "verify-xposed-rom-contents: error: $*" >&2; exit 1; }

[ -f "$ZIP" ] || die "missing zip: $ZIP"
command -v unzip >/dev/null || die "missing unzip"
command -v debugfs >/dev/null || die "missing debugfs"

SOLAR_ROM_BUILD_DIR="${SOLAR_ROM_BUILD_DIR:-$HOME/.cache/solar-rom-build}"
tmpdir=$(mktemp -d "$SOLAR_ROM_BUILD_DIR/verify-xposed-XXXXXX")
trap 'rm -rf "$tmpdir"' EXIT

unzip -q "$ZIP" system.img -d "$tmpdir"
sys="$tmpdir/system.img"
[ -f "$sys" ] || die "missing system.img in zip"

if [ "$API" = "auto" ]; then
    if unzip -l "$ZIP" 2>/dev/null | grep -q MT6582; then
        API=19
    else
        API=17
    fi
fi

require_path() {
    debugfs -R "stat $1" "$sys" 2>/dev/null | grep -q 'Type: regular' \
        || die "missing $1 in system.img"
}

errors=0
fail() { echo "verify-xposed-rom-contents: FAIL: $*" >&2; errors=$((errors + 1)); }

echo "==> verify-xposed-rom-contents: $(basename "$ZIP") (API $API)"

require_path /bin/app_process.orig || fail "/bin/app_process.orig"
require_path /framework/XposedBridge.jar || fail "/framework/XposedBridge.jar"
require_path /etc/solar/XposedBridge.jar || fail "/etc/solar/XposedBridge.jar"
require_path /xposed.prop || fail "/xposed.prop"
require_path /app/XposedInstaller.apk || fail "/app/XposedInstaller.apk"
require_path /app/SolarThemeFont.apk || fail "/app/SolarThemeFont.apk"
if [ "$API" = "17" ] || [ "$API" = "18" ]; then
    require_path /app/SolarContextBridgeY1.apk || fail "/app/SolarContextBridgeY1.apk"
else
    require_path /app/SolarContextBridgeY2.apk || fail "/app/SolarContextBridgeY2.apk"
fi
require_path /etc/init.d/99XposedInit.sh || fail "/etc/init.d/99XposedInit.sh"

init_hook="$(debugfs -R "cat /etc/init.d/99XposedInit.sh" "$sys" 2>/dev/null || true)"
echo "$init_hook" | grep -q 'com.solar.launcher.xposed.themefont' \
    || fail "99XposedInit.sh must enable com.solar.launcher.xposed.themefont"

prop="$(debugfs -R "cat /xposed.prop" "$sys" 2>/dev/null || true)"
echo "$prop" | grep -q '^version=' || fail "xposed.prop missing version="
echo "$prop" | grep -q "^sdk=${API}" || fail "xposed.prop sdk= mismatch (expected $API)"

ap="$tmpdir/app_process"
orig="$tmpdir/app_process.orig"
( cd "$tmpdir" && debugfs -R "dump /bin/app_process app_process" system.img >/dev/null 2>&1 )
( cd "$tmpdir" && debugfs -R "dump /bin/app_process.orig app_process.orig" system.img >/dev/null 2>&1 )
if [ -f "$ap" ] && [ -f "$orig" ] && cmp -s "$ap" "$orig"; then
    fail "app_process identical to app_process.orig"
fi
if [ -f "$ap" ] && ! strings "$ap" 2>/dev/null | grep -q 'with Xposed support'; then
    fail "app_process missing Xposed support string"
fi

[ "$errors" -eq 0 ] && echo "==> verify-xposed-rom-contents: OK" && exit 0
die "$errors check(s) failed"
