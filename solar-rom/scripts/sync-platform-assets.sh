#!/usr/bin/env bash
# 2026-07-05 — Bundles Xposed vendor trees + production modules into APK assets and emits manifest.json.
# APK/ROM parity: mirrors install-xposed-system.sh paths; SolarPlatformPrep reads manifest at runtime.
# When changing: bump prepVersion below if ladder/deprecated/modules change; update XposedModuleRegistry;
#   install-xposed-system.sh + 99XposedInit.sh + verify-xposed-rom-contents.sh; run verify-platform-assets.sh.
# prepVersion bump checklist:
#   1) New module row in modules[] (y1/y2/both)  2) deprecated[] for removed /system APKs
#   3) files[] for new init.d hooks  4) rebuild APK via scripts/build.sh (always runs this script)
# Reversal: delete script; APK ships without platform repair kit (ROM-only Xposed again).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VENDOR="$ROOT/solar-rom/vendor/xposed"
DST="$ROOT/app/src/main/assets/platform"
INIT_SRC="$ROOT/solar-rom/system/99XposedInit.sh"

die() { echo "sync-platform-assets: $*" >&2; exit 1; }

# Build production module APKs when missing (same preflight as ROM build).
build_if_missing() {
    local path="$1" script="$2"
    if [ -f "$path" ]; then return 0; fi
    [ -x "$SCRIPT_DIR/$script" ] || die "missing $path and $script"
    "$SCRIPT_DIR/$script"
    [ -f "$path" ] || die "build failed: $path"
}

build_if_missing "$VENDOR/XposedInstaller.apk" "build-xposed-installer-apk.sh"
build_if_missing "$VENDOR/solar-context-bridge/SolarContextBridgeY1.apk" "build-context-bridge-apk.sh"
build_if_missing "$VENDOR/solar-context-bridge/SolarContextBridgeY2.apk" "build-context-bridge-apk.sh"
build_if_missing "$VENDOR/solar-theme-font/SolarThemeFont.apk" "build-theme-font-apk.sh"
build_if_missing "$VENDOR/solar-rockbox-ime/SolarRockboxIme.apk" "build-rockbox-xposed-apks.sh"
build_if_missing "$VENDOR/solar-rockbox-compat/SolarRockboxCompat.apk" "build-rockbox-xposed-apks.sh"
build_if_missing "$VENDOR/solar-notpipe-bridge/SolarNotPipeBridge.apk" "build-notpipe-bridge-apk.sh"

# 2026-07-06 — notPipe YouTube client APK (third-party; pinned v0.3.0).
# 2026-07-14 — Prefer Solarized APK (WakeService + SolarCmdReceiver); never overwrite with upstream minify.
chmod +x "$SCRIPT_DIR/fetch-notpipe-apk.sh"
NOTPIPE_CACHE="$("$SCRIPT_DIR/fetch-notpipe-apk.sh")"
NOTPIPE_UPSTREAM_CACHE="$NOTPIPE_CACHE/notPipe-0.3.0-release.apk"
NOTPIPE_SOLAR_REF="$ROOT/reference/NotPipe reference/notPipe-0.3.0-release.apk"
NOTPIPE_SRC=""
if [ -f "$NOTPIPE_SOLAR_REF" ] && python3 -c "import zipfile,sys;z=zipfile.ZipFile(sys.argv[1]);d=z.read('classes.dex');sys.exit(0 if b'SolarWakeService' in d and b'SolarCmdReceiver' in d else 1)" "$NOTPIPE_SOLAR_REF" 2>/dev/null; then
    NOTPIPE_SRC="$NOTPIPE_SOLAR_REF"
    echo "==> Using Solarized notPipe reference: $NOTPIPE_SRC"
elif [ -x "$SCRIPT_DIR/build-notpipe-solar-apk.sh" ]; then
    echo "==> Building Solarized notPipe APK (WakeService + SolarCmdReceiver)"
    "$SCRIPT_DIR/build-notpipe-solar-apk.sh"
    NOTPIPE_SRC="$NOTPIPE_SOLAR_REF"
fi
[ -f "$NOTPIPE_SRC" ] || NOTPIPE_SRC="$NOTPIPE_UPSTREAM_CACHE"
[ -f "$NOTPIPE_SRC" ] || die "missing notPipe APK — fetch/build failed"

# 2026-07-05 — Companion global context modal APK (Phase 1); bundled for platform self-heal.
COMPANION_APK="$ROOT/global-context-modal/build/outputs/apk/debug/global-context-modal-debug.apk"
if [ ! -f "$COMPANION_APK" ]; then
    (cd "$ROOT" && ./gradlew :global-context-modal:assembleDebug -q)
fi
[ -f "$COMPANION_APK" ] || die "missing companion APK — run ./gradlew :global-context-modal:assembleDebug"

# 2026-07-06 — Solar Home Helper middle-man APK; permanent PM preferred HOME on Y1/Y2.
HELPER_APK="$ROOT/launcher-helper/build/outputs/apk/debug/launcher-helper-debug.apk"
if [ ! -f "$HELPER_APK" ]; then
    (cd "$ROOT" && ./gradlew :launcher-helper:assembleDebug -q)
fi
[ -f "$HELPER_APK" ] || die "missing SolarHomeHelper APK — run ./gradlew :launcher-helper:assembleDebug"

[ -d "$VENDOR/api17-arm" ] || die "missing $VENDOR/api17-arm"
[ -d "$VENDOR/api19-arm" ] || die "missing $VENDOR/api19-arm"
[ -f "$INIT_SRC" ] || die "missing $INIT_SRC"

mkdir -p "$DST/xposed/api17-arm" "$DST/xposed/api19-arm" "$DST/init" "$DST/scripts" "$DST/companion" "$DST/thirdparty" "$DST/rockbox"

# 2026-07-06 — Rockbox Y2 compat bundle (manifest resign + staged libs + dot-rockbox); ROM build optional.
sync_rockbox_platform_assets() {
    local rb_dst="$DST/rockbox"
    local cache patched work_rb
    chmod +x "$SCRIPT_DIR/fetch-rockbox-y1-y2-assets.sh" \
        "$SCRIPT_DIR/patch-rockbox-y2.sh" \
        "$SCRIPT_DIR/extract-rockbox-staged-assets.sh"
    cache="$("$SCRIPT_DIR/fetch-rockbox-y1-y2-assets.sh")"
    [ -f "$cache/org.rockbox.apk" ] && [ -f "$cache/librockbox.so" ] \
        || die "fetch-rockbox-y1-y2-assets.sh missing org.rockbox.apk or librockbox.so"
    work_rb="$(mktemp -d "${TMPDIR:-/tmp}/solar-rockbox-platform-XXXXXX")"
    patched="$work_rb/org.rockbox-y2.apk"
    "$SCRIPT_DIR/patch-rockbox-y2.sh" "$cache/org.rockbox.apk" "$patched"
    cp "$cache/org.rockbox.apk" "$rb_dst/org.rockbox-y1.apk"
    cp "$patched" "$rb_dst/org.rockbox-y2.apk"
    cp "$cache/librockbox.so" "$rb_dst/librockbox-system.so"
    rm -rf "$rb_dst/staged-libs" "$rb_dst/dot-rockbox"
    mkdir -p "$rb_dst/staged-libs" "$rb_dst/dot-rockbox"
    "$SCRIPT_DIR/extract-rockbox-staged-assets.sh" "$patched" "$rb_dst/staged-libs" "$rb_dst/dot-rockbox"
    cp "$ROOT/solar-rom/system/solar-rb-launch" "$rb_dst/solar-rb-launch"
    chmod 755 "$rb_dst/solar-rb-launch"
    cp "$ROOT/solar-rom/system/rockbox-y2-config.cfg" "$rb_dst/rockbox-y2-config.cfg"
    cp "$SCRIPT_DIR/sync-rockbox-libs.sh" "$rb_dst/sync-rockbox-libs.sh"
    cp "$SCRIPT_DIR/sync-rockbox-assets.sh" "$rb_dst/sync-rockbox-assets.sh"
    chmod 755 "$rb_dst/sync-rockbox-libs.sh" "$rb_dst/sync-rockbox-assets.sh"
    python3 - "$rb_dst" <<'PY'
import json, os, sys
root = sys.argv[1]
files = []
def add_tree(subdir, dest_prefix, mode="644"):
    base = os.path.join(root, subdir)
    if not os.path.isdir(base):
        return
    for dirpath, _, names in os.walk(base):
        for name in sorted(names):
            full = os.path.join(dirpath, name)
            rel = os.path.relpath(full, root).replace(os.sep, "/")
            dest = dest_prefix + rel[len(subdir) + 1:]
            files.append({"asset": "rockbox/" + rel, "dest": dest, "mode": mode})
add_tree("staged-libs", "/system/etc/solar/rockbox-libs/", "644")
add_tree("dot-rockbox", "/system/etc/solar/rockbox-dot-rockbox/", "644")
for rel, dest, mode in [
    ("solar-rb-launch", "/system/xbin/solar-rb-launch", "755"),
    ("rockbox-y2-config.cfg", "/system/etc/solar/rockbox-y2-config.cfg", "644"),
    ("sync-rockbox-libs.sh", "/system/etc/solar/sync-rockbox-libs.sh", "755"),
    ("sync-rockbox-assets.sh", "/system/etc/solar/sync-rockbox-assets.sh", "755"),
    ("librockbox-system.so", "/system/lib/librockbox.so", "644"),
]:
    if os.path.isfile(os.path.join(root, rel)):
        files.append({"asset": "rockbox/" + rel, "dest": dest, "mode": mode})
with open(os.path.join(root, "stage-index.json"), "w", encoding="utf-8") as f:
    json.dump({"files": files}, f, separators=(",", ":"))
print("==> Rockbox stage-index: %d files" % len(files))
PY
    rm -rf "$work_rb"
    echo "==> Rockbox platform assets bundled under $rb_dst"
}
sync_rockbox_platform_assets

# Vendor framework trees (app_process, XposedBridge.jar, xposed.prop).
for api in api17-arm api19-arm; do
    for f in app_process XposedBridge.jar xposed.prop; do
        cp "$VENDOR/$api/$f" "$DST/xposed/$api/$f"
    done
done

cp "$VENDOR/XposedInstaller.apk" "$DST/xposed/XposedInstaller.apk"
cp "$VENDOR/solar-context-bridge/SolarContextBridgeY1.apk" "$DST/xposed/SolarContextBridgeY1.apk"
cp "$VENDOR/solar-context-bridge/SolarContextBridgeY2.apk" "$DST/xposed/SolarContextBridgeY2.apk"
cp "$VENDOR/solar-theme-font/SolarThemeFont.apk" "$DST/xposed/SolarThemeFont.apk"
cp "$VENDOR/solar-rockbox-ime/SolarRockboxIme.apk" "$DST/xposed/SolarRockboxIme.apk"
cp "$VENDOR/solar-rockbox-compat/SolarRockboxCompat.apk" "$DST/xposed/SolarRockboxCompat.apk"
cp "$VENDOR/solar-notpipe-bridge/SolarNotPipeBridge.apk" "$DST/xposed/SolarNotPipeBridge.apk"
cp "$NOTPIPE_SRC" "$DST/thirdparty/notPipe-0.3.0-release.apk"
cp "$COMPANION_APK" "$DST/companion/SolarGlobalContextModal.apk"
cp "$HELPER_APK" "$DST/companion/SolarHomeHelper.apk"
cp "$INIT_SRC" "$DST/init/99XposedInit.sh"
chmod 755 "$DST/init/99XposedInit.sh"

# Shell companion for bulk root copies (Java invokes after asset extract).
PREP_SH="$ROOT/app/src/main/assets/scripts/solar-platform-prep.sh"
mkdir -p "$(dirname "$PREP_SH")"
cat > "$PREP_SH" <<'EOF'
#!/system/bin/sh
# 2026-07-05 — Idempotent /system file staging from extracted platform-prep cache.
# Usage: solar-platform-prep.sh copy <src> <dest> <mode>
# Emits: SOLAR_PREP_PROGRESS pct message
progress() { echo "SOLAR_PREP_PROGRESS $1 $2"; }
case "$1" in
copy)
  SRC="$2"; DEST="$3"; MODE="${4:-644}"
  [ -f "$SRC" ] || exit 1
  mount -o remount,rw /system 2>/dev/null || true
  mkdir -p "$(dirname "$DEST")"
  cp "$SRC" "$DEST" || exit 1
  chmod "$MODE" "$DEST" 2>/dev/null || true
  progress 100 "copied $(basename "$DEST")"
  ;;
*) exit 1 ;;
esac
EOF
chmod +x "$PREP_SH"

sha256_file() {
    sha256sum "$1" | awk '{print $1}'
}

apk_version_code() {
    local apk="$1"
    local BT=""
    ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
    for v in 35.0.0 34.0.0 36.0.0 28.0.3; do
        [ -x "$ANDROID_HOME/build-tools/$v/aapt" ] && BT="$ANDROID_HOME/build-tools/$v/aapt" && break
    done
    if [ -n "$BT" ]; then
        "$BT" dump badging "$apk" 2>/dev/null | sed -n "s/^.*versionCode='\([0-9]*\)'.*/\1/p" | head -1
        return 0
    fi
    echo "1"
}

Y1_VC="$(apk_version_code "$DST/xposed/SolarContextBridgeY1.apk")"
Y2_VC="$(apk_version_code "$DST/xposed/SolarContextBridgeY2.apk")"
TF_VC="$(apk_version_code "$DST/xposed/SolarThemeFont.apk")"
RB_IME_VC="$(apk_version_code "$DST/xposed/SolarRockboxIme.apk")"
RB_COMPAT_VC="$(apk_version_code "$DST/xposed/SolarRockboxCompat.apk")"
NP_BRIDGE_VC="$(apk_version_code "$DST/xposed/SolarNotPipeBridge.apk")"
NOTPIPE_VC="$(apk_version_code "$DST/thirdparty/notPipe-0.3.0-release.apk")"
GC_VC="$(apk_version_code "$DST/companion/SolarGlobalContextModal.apk")"
HELPER_VC="$(apk_version_code "$DST/companion/SolarHomeHelper.apk")"
RB_Y1_VC="$(apk_version_code "$DST/rockbox/org.rockbox-y1.apk")"
RB_Y2_VC="$(apk_version_code "$DST/rockbox/org.rockbox-y2.apk")"
RB_Y1_SHA="$(sha256_file "$DST/rockbox/org.rockbox-y1.apk")"
RB_Y2_SHA="$(sha256_file "$DST/rockbox/org.rockbox-y2.apk")"
RB_LIB_SHA="$(sha256_file "$DST/rockbox/librockbox-system.so")"

# manifest.json — parsed by PlatformPrepManifest.java at runtime.
cat > "$DST/manifest.json" <<EOF
{
  "prepVersion": 17,
  "framework": {
    "api17": {
      "appProcess": "xposed/api17-arm/app_process",
      "bridgeJar": "xposed/api17-arm/XposedBridge.jar",
      "xposedProp": "xposed/api17-arm/xposed.prop"
    },
    "api19": {
      "appProcess": "xposed/api19-arm/app_process",
      "bridgeJar": "xposed/api19-arm/XposedBridge.jar",
      "xposedProp": "xposed/api19-arm/xposed.prop"
    },
    "installerApk": "xposed/XposedInstaller.apk",
    "initHook": "init/99XposedInit.sh",
    "systemPaths": {
      "appProcess": "/system/bin/app_process",
      "appProcessOrig": "/system/bin/app_process.orig",
      "bridgeJarFramework": "/system/framework/XposedBridge.jar",
      "bridgeJarSolar": "/system/etc/solar/XposedBridge.jar",
      "xposedProp": "/system/xposed.prop",
      "installerApk": "/system/app/XposedInstaller.apk",
      "initHook": "/system/etc/init.d/99XposedInit.sh"
    }
  },
  "modules": [
    {
      "pkg": "com.solar.launcher.xposed.bridge.y1",
      "asset": "xposed/SolarContextBridgeY1.apk",
      "systemApk": "/system/app/SolarContextBridgeY1.apk",
      "required": true,
      "device": "y1",
      "versionCode": ${Y1_VC:-1},
      "sha256": "$(sha256_file "$DST/xposed/SolarContextBridgeY1.apk")"
    },
    {
      "pkg": "com.solar.launcher.xposed.bridge.y2",
      "asset": "xposed/SolarContextBridgeY2.apk",
      "systemApk": "/system/app/SolarContextBridgeY2.apk",
      "required": true,
      "device": "y2",
      "versionCode": ${Y2_VC:-1},
      "sha256": "$(sha256_file "$DST/xposed/SolarContextBridgeY2.apk")"
    },
    {
      "pkg": "com.solar.launcher.xposed.themefont",
      "asset": "xposed/SolarThemeFont.apk",
      "systemApk": "/system/app/SolarThemeFont.apk",
      "required": true,
      "device": "both",
      "versionCode": ${TF_VC:-1},
      "sha256": "$(sha256_file "$DST/xposed/SolarThemeFont.apk")"
    },
    {
      "pkg": "com.solar.launcher.xposed.rockbox.ime",
      "asset": "xposed/SolarRockboxIme.apk",
      "systemApk": "/system/app/SolarRockboxIme.apk",
      "required": true,
      "device": "both",
      "versionCode": ${RB_IME_VC:-1},
      "sha256": "$(sha256_file "$DST/xposed/SolarRockboxIme.apk")"
    },
    {
      "pkg": "com.solar.launcher.xposed.rockbox.compat",
      "asset": "xposed/SolarRockboxCompat.apk",
      "systemApk": "/system/app/SolarRockboxCompat.apk",
      "required": true,
      "device": "y2",
      "versionCode": ${RB_COMPAT_VC:-1},
      "sha256": "$(sha256_file "$DST/xposed/SolarRockboxCompat.apk")"
    },
    {
      "pkg": "com.solar.launcher.xposed.notpipe",
      "asset": "xposed/SolarNotPipeBridge.apk",
      "systemApk": "/system/app/SolarNotPipeBridge.apk",
      "required": true,
      "device": "both",
      "versionCode": ${NP_BRIDGE_VC:-1},
      "sha256": "$(sha256_file "$DST/xposed/SolarNotPipeBridge.apk")"
    },
    {
      "pkg": "io.github.gohoski.notpipe",
      "asset": "thirdparty/notPipe-0.3.0-release.apk",
      "systemApk": "/system/app/io.github.gohoski.notpipe.apk",
      "required": true,
      "device": "both",
      "versionCode": ${NOTPIPE_VC:-1},
      "sha256": "$(sha256_file "$DST/thirdparty/notPipe-0.3.0-release.apk")"
    },
    {
      "pkg": "com.solar.launcher.globalcontext",
      "asset": "companion/SolarGlobalContextModal.apk",
      "systemApk": "/system/app/SolarGlobalContextModal.apk",
      "required": true,
      "device": "both",
      "versionCode": ${GC_VC:-1},
      "sha256": "$(sha256_file "$DST/companion/SolarGlobalContextModal.apk")"
    },
    {
      "pkg": "com.solar.launcher.homehelper",
      "asset": "companion/SolarHomeHelper.apk",
      "systemApk": "/system/app/SolarHomeHelper.apk",
      "required": true,
      "device": "both",
      "versionCode": ${HELPER_VC:-1},
      "sha256": "$(sha256_file "$DST/companion/SolarHomeHelper.apk")"
    }
  ],
  "files": [
    {"asset": "init/99XposedInit.sh", "dest": "/system/etc/init.d/99XposedInit.sh", "mode": "755"}
  ],
  "rockbox": {
    "pkg": "org.rockbox",
    "stageIndex": "rockbox/stage-index.json",
    "y1Apk": {
      "asset": "rockbox/org.rockbox-y1.apk",
      "systemApk": "/system/app/org.rockbox.apk",
      "device": "y1",
      "versionCode": ${RB_Y1_VC:-1},
      "sha256": "${RB_Y1_SHA}"
    },
    "y2Apk": {
      "asset": "rockbox/org.rockbox-y2.apk",
      "systemApk": "/system/app/org.rockbox.apk",
      "device": "y2",
      "versionCode": ${RB_Y2_VC:-1},
      "sha256": "${RB_Y2_SHA}"
    },
    "systemLib": {
      "asset": "rockbox/librockbox-system.so",
      "dest": "/system/lib/librockbox.so",
      "device": "y2",
      "sha256": "${RB_LIB_SHA}"
    }
  },
  "deprecated": [
    {
      "systemApk": "/system/app/SolarContextBridge.apk",
      "pkg": "com.solar.launcher.xposed.bridge",
      "device": "both",
      "reason": "legacy unified bridge before Y1/Y2 split"
    },
    {
      "systemApk": "/system/app/SolarContextBridgeY1.apk",
      "pkg": "com.solar.launcher.xposed.bridge.y1",
      "device": "y2",
      "reason": "wrong-family bridge on Y2"
    },
    {
      "systemApk": "/system/app/SolarContextBridgeY2.apk",
      "pkg": "com.solar.launcher.xposed.bridge.y2",
      "device": "y1",
      "reason": "wrong-family bridge on Y1"
    }
  ]
}
EOF

chmod +x "$ROOT/solar-rom/scripts/verify-platform-assets.sh"
"$ROOT/solar-rom/scripts/verify-platform-assets.sh"
echo "sync-platform-assets: OK -> $DST"
