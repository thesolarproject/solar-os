#!/usr/bin/env bash
# Build release APK + all three Solar ROM zips (Y1 type A/B, Y2 ATA) — mirrors CI.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh"
cd "$ROOT"

export SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-$(date -u +%s)}"
APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
DIST="$ROOT/dist"
BUILD_ROM="$ROOT/solar-rom/scripts/build-rom.sh"
SKIP_APK="${SKIP_APK:-0}"

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || { echo "ERROR: missing $1" >&2; exit 1; }
}

require_cmd curl
require_cmd unzip

chmod +x "$BUILD_ROM" "$ROOT/solar-rom/scripts/"*.sh "$ROOT/scripts/build.sh"

if [ "$SKIP_APK" != "1" ]; then
    echo "==> Building release APK (as $(id -un))"
    "$ROOT/scripts/build.sh"
fi

[[ -f "$APK" ]] || { echo "ERROR: missing $APK" >&2; exit 1; }

# Platform keystore needed for Y2 patch-rockbox-y2.sh even when SKIP_APK=1.
chmod +x "$ROOT/scripts/ensure-platform-keystore.sh" "$ROOT/solar-rom/scripts/sync-y1-assets.sh"
if [ -f "$ROOT/.gradle/platform.keystore" ]; then
    echo "==> Using existing platform.keystore (from APK build or prior run)"
else
    "$ROOT/scripts/ensure-platform-keystore.sh"
fi
"$ROOT/solar-rom/scripts/sync-y1-assets.sh"

mkdir -p "$DIST"

# ROM loop-mount needs root; APK/Gradle must run as the repo owner (git safe.directory).
ROM_SCRIPT="$DIST/.build-roms-inner.sh"
cat >"$ROM_SCRIPT" <<EOF
#!/usr/bin/env bash
set -euo pipefail
export HOME="\${HOME:-/root}"
export JAVA_HOME="${JAVA_HOME:-}"
export ANDROID_HOME="${ANDROID_HOME:-}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
export PATH="/usr/sbin:/sbin:${PATH:-/usr/bin:/bin}"
ROOT="$ROOT"
require_cmd() { command -v "\$1" >/dev/null 2>&1 || { echo "ERROR: missing \$1" >&2; exit 1; }; }
# Docker --privileged runs as root without sudo package — shim before require_cmd sudo.
if [ -f "\$ROOT/solar-rom/scripts/lib-root.sh" ]; then
    # shellcheck source=/dev/null
    source "\$ROOT/solar-rom/scripts/lib-root.sh"
    ensure_sudo_shim_when_root
fi
require_cmd simg2img img2simg e2fsck tune2fs sudo curl unzip binutils
BUILD_ROM="$BUILD_ROM"
APK="$APK"
DIST="$DIST"
chmod +x "\$BUILD_ROM" "\$ROOT/solar-rom/scripts/"*.sh
echo "==> Building Y1 type A ROM (dist/rom.zip)"
sudo modprobe loop max_loop=64 2>/dev/null || true
"\$BUILD_ROM" a --apk "\$APK" "\$DIST/rom.zip"
echo "==> Building Y1 type B ROM (dist/rom_type_b.zip)"
sudo modprobe loop max_loop=64 2>/dev/null || true
"\$BUILD_ROM" b --apk "\$APK" "\$DIST/rom_type_b.zip"
echo "==> Building Y2 ATA ROM (dist/rom_y2.zip)"
sudo modprobe loop max_loop=64 2>/dev/null || true
"\$BUILD_ROM" y2 --apk "\$APK" "\$DIST/rom_y2.zip"
echo "==> ROM archive sanity checks"
for z in "\$DIST/rom.zip" "\$DIST/rom_type_b.zip" "\$DIST/rom_y2.zip"; do
    [[ -f "\$z" ]] || { echo "ERROR: missing \$z" >&2; exit 1; }
    ls -lh "\$z"
done
scatter_ok() { [ "\$(unzip -l "\$1" 2>/dev/null | grep -c "\$2" || true)" -ge 1 ]; }
scatter_ok "\$DIST/rom.zip" MT6572_Android_scatter.txt \
    || { echo "ERROR: rom.zip missing MT6572 scatter" >&2; exit 1; }
scatter_ok "\$DIST/rom_type_b.zip" MT6572_Android_scatter.txt \
    || { echo "ERROR: rom_type_b.zip missing MT6572 scatter" >&2; exit 1; }
scatter_ok "\$DIST/rom_y2.zip" MT6582_Android_scatter.txt \
    || { echo "ERROR: rom_y2.zip missing MT6582 scatter" >&2; exit 1; }
"\$ROOT/solar-rom/scripts/verify-y1-rom-contents.sh" "\$DIST/rom.zip"
"\$ROOT/solar-rom/scripts/verify-y1-rom-contents.sh" "\$DIST/rom_type_b.zip"
"\$ROOT/solar-rom/scripts/verify-y2-rom-flash.sh" "\$DIST/rom_y2.zip"
"\$ROOT/solar-rom/scripts/verify-y2-rom-contents.sh" "\$DIST/rom_y2.zip"
"\$ROOT/solar-rom/scripts/verify-xposed-rom-contents.sh" "\$DIST/rom.zip" 17
"\$ROOT/solar-rom/scripts/verify-xposed-rom-contents.sh" "\$DIST/rom_type_b.zip" 17
"\$ROOT/solar-rom/scripts/verify-xposed-rom-contents.sh" "\$DIST/rom_y2.zip" 19
cp -f "\$DIST/rom.zip" "\$ROOT/rom.zip"
cp -f "\$DIST/rom_type_b.zip" "\$ROOT/rom_type_b.zip"
cp -f "\$DIST/rom_y2.zip" "\$ROOT/rom_y2.zip"
echo "==> All ROM builds complete"
EOF
chmod +x "$ROM_SCRIPT"

if [ "$(id -u)" -eq 0 ]; then
    echo "==> Building ROMs (already root)"
    bash "$ROM_SCRIPT"
elif sudo -n true 2>/dev/null; then
    echo "==> Building ROMs (sudo)"
    sudo bash "$ROM_SCRIPT"
elif command -v systemd-run >/dev/null 2>&1; then
    echo "==> Building ROMs (systemd-run — loop mount needs root)"
    systemd-run --wait --collect bash "$ROM_SCRIPT"
else
    echo "ERROR: ROM build needs root (sudo, docker --privileged, or systemd-run) for loop-mount" >&2
    exit 1
fi

echo "    $DIST/rom.zip"
echo "    $DIST/rom_type_b.zip"
echo "    $DIST/rom_y2.zip"
