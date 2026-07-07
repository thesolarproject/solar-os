#!/usr/bin/env bash
# 2026-07-05 — Verify app/src/main/assets/platform matches vendor sources.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DST="$ROOT/app/src/main/assets/platform"
VENDOR="$ROOT/solar-rom/vendor/xposed"

die() { echo "verify-platform-assets: $*" >&2; exit 1; }

[ -f "$DST/manifest.json" ] || die "missing manifest — run sync-platform-assets.sh"
[ -f "$DST/xposed/XposedInstaller.apk" ] || die "missing XposedInstaller.apk asset"
[ -f "$DST/xposed/SolarContextBridgeY1.apk" ] || die "missing SolarContextBridgeY1.apk asset"
[ -f "$DST/xposed/SolarContextBridgeY2.apk" ] || die "missing SolarContextBridgeY2.apk asset"
[ -f "$DST/xposed/SolarThemeFont.apk" ] || die "missing SolarThemeFont.apk asset"
[ -f "$DST/xposed/SolarRockboxIme.apk" ] || die "missing SolarRockboxIme.apk asset"
[ -f "$DST/xposed/SolarRockboxCompat.apk" ] || die "missing SolarRockboxCompat.apk asset"
[ -f "$DST/xposed/SolarNotPipeBridge.apk" ] || die "missing SolarNotPipeBridge.apk asset"
[ -f "$DST/thirdparty/notPipe-0.3.0-release.apk" ] || die "missing notPipe APK asset"
[ -f "$DST/companion/SolarGlobalContextModal.apk" ] || die "missing SolarGlobalContextModal.apk asset"
[ -f "$DST/companion/SolarHomeHelper.apk" ] || die "missing SolarHomeHelper.apk asset"
[ -f "$DST/init/99XposedInit.sh" ] || die "missing 99XposedInit.sh asset"
for api in api17-arm api19-arm; do
    for f in app_process XposedBridge.jar xposed.prop; do
        [ -f "$DST/xposed/$api/$f" ] || die "missing xposed/$api/$f"
    done
done
[ -f "$ROOT/app/src/main/assets/scripts/solar-platform-prep.sh" ] || die "missing solar-platform-prep.sh"
chmod +x "$ROOT/solar-rom/scripts/verify-platform-rockbox-assets.sh"
"$ROOT/solar-rom/scripts/verify-platform-rockbox-assets.sh"
echo "verify-platform-assets: OK"
