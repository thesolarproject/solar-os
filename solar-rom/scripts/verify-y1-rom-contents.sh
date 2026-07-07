#!/usr/bin/env bash
# Post-zip Y1 ROM audit — rockbox-y1 base must keep org.rockbox + permissive su after Solar overlay.
# Usage: verify-y1-rom-contents.sh [rom.zip|rom_type_b.zip]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib-android-tools.sh
source "$SCRIPT_DIR/lib-android-tools.sh"

ZIP="${1:-rom.zip}"
die() { echo "verify-y1-rom-contents: error: $*" >&2; exit 1; }

[ -f "$ZIP" ] || die "usage: $0 [rom.zip|rom_type_b.zip]"
command -v unzip >/dev/null 2>&1 || die "missing unzip"
command -v debugfs >/dev/null 2>&1 || die "missing debugfs (e2fsprogs)"

AAPT="$(find_android_build_tool aapt)" || die "missing aapt (set ANDROID_HOME or add build-tools to PATH)"

SOLAR_ROM_BUILD_DIR="${SOLAR_ROM_BUILD_DIR:-$HOME/.cache/solar-rom-build}"
mkdir -p "$SOLAR_ROM_BUILD_DIR"
tmpdir=$(mktemp -d "$SOLAR_ROM_BUILD_DIR/verify-y1-XXXXXX")
trap 'rm -rf "$tmpdir"' EXIT

echo "==> verify-y1-rom-contents: $ZIP"
unzip -q "$ZIP" system.img -d "$tmpdir"
sys="$tmpdir/system.img"
[ -f "$sys" ] || die "missing system.img in zip"

debugfs_cat() {
    debugfs -R "cat $1" "$sys" 2>/dev/null || true
}

# Paths with spaces break debugfs -R dump; write into $tmpdir via relative names.
debugfs_dump() {
    local inode_path="$1" dest_base="$2"
    ( cd "$tmpdir" && debugfs -R "dump ${inode_path} ${dest_base}" system.img ) >/dev/null 2>&1
}

require_path() {
    if debugfs -R "stat $1" "$sys" 2>/dev/null | grep -q 'Type: regular'; then
        return 0
    fi
    debugfs -R "ls -l $1" "$sys" 2>/dev/null | grep -q . \
        || die "missing $1 in system.img"
}

errors=0
fail() { echo "verify-y1-rom-contents: FAIL: $*" >&2; errors=$((errors + 1)); }

require_path /app/com.solar.launcher.apk || fail "com.solar.launcher.apk"
require_path /app/SolarHomeHelper.apk || fail "SolarHomeHelper.apk (HOME middle-man)"
require_path /app/org.rockbox.apk || fail "org.rockbox.apk (rockbox-y1 base)"
require_path /lib/librockbox.so || fail "librockbox.so"

rb_apk="$tmpdir/org.rockbox.apk"
debugfs_dump /app/org.rockbox.apk org.rockbox.apk || fail "could not extract org.rockbox.apk"
[ -s "$rb_apk" ] || fail "org.rockbox.apk empty"

# grep -q + pipefail makes unzip SIGPIPE — count libmisc lines instead.
rb_misc_count=$(unzip -l "$rb_apk" 2>/dev/null | grep -c 'lib/armeabi/libmisc.so' || true)
[ "${rb_misc_count:-0}" -ge 1 ] || fail "org.rockbox.apk missing lib/armeabi/libmisc.so"

rb_so_count=$(unzip -l "$rb_apk" 2>/dev/null | grep -c 'lib/armeabi/.*\.so' || true)
[ "${rb_so_count:-0}" -ge 35 ] || fail "org.rockbox.apk has ${rb_so_count:-0} native libs (expected >=35)"

for p in \
    /etc/solar/switch-to-stock.sh \
    /etc/solar/switch-to-rockbox.sh \
    /etc/solar/sync-y1-keymap.sh \
    /etc/solar/sync-rockbox-libs.sh \
    /etc/solar/sync-rockbox-assets.sh \
    /etc/solar/disable-rockbox-for-solar.sh \
    /etc/solar/apply-preferred-home-boot.sh \
    /etc/solar/disable-large-font-accessibility.sh \
    /etc/solar/enable-gpu-performance.sh \
    /etc/solar/solar-rescue-exec.sh \
    /etc/solar/solar-rescue-daemon.sh \
    /etc/solar/solar-rescue-hud-watch.sh \
    /etc/solar/Y1-Rockbox.kl \
    /etc/init.d/99SolarInit.sh \
    /etc/init.d/99Y1ButtonScript \
    /etc/init.d/99SuperSUDaemon \
    /etc/init.d/99XposedInit.sh \
    /etc/install-recovery.sh \
    /etc/install-recovery-2.sh \
    /framework/XposedBridge.jar \
    /xposed.prop \
    /app/XposedInstaller.apk \
    /app/FMRadio.apk \
    /lib/libfmjni.so \
    /app/SolarContextBridgeY1.apk \
    /app/SolarThemeFont.apk \
    /app/SolarRockboxIme.apk \
    /app/SolarNotPipeBridge.apk \
    /app/io.github.gohoski.notpipe.apk; do
    require_path "$p" || fail "missing $p"
done

if ! debugfs_cat /etc/init.d/99XposedInit.sh | grep -q 'com.solar.launcher.xposed.themefont'; then
    fail "99XposedInit.sh must enable com.solar.launcher.xposed.themefont"
fi
if ! debugfs_cat /etc/init.d/99XposedInit.sh | grep -q 'com.solar.launcher.xposed.bridge.y1'; then
    fail "99XposedInit.sh must enable com.solar.launcher.xposed.bridge.y1"
fi
if ! debugfs_cat /etc/init.d/99XposedInit.sh | grep -q 'com.solar.launcher.xposed.rockbox.ime'; then
    fail "99XposedInit.sh must enable com.solar.launcher.xposed.rockbox.ime"
fi
if ! debugfs_cat /etc/init.d/99XposedInit.sh | grep -q 'com.solar.launcher.xposed.notpipe'; then
    fail "99XposedInit.sh must enable com.solar.launcher.xposed.notpipe"
fi

if ! debugfs_cat /etc/init.d/99SolarInit.sh | grep -q 'apply-preferred-home-boot.sh'; then
    fail "99SolarInit.sh must run apply-preferred-home-boot.sh"
fi
if ! debugfs_cat /etc/init.d/99SolarInit.sh | grep -q 'disable-large-font-accessibility.sh'; then
    fail "99SolarInit.sh must reset large-font accessibility on boot"
fi
if ! debugfs_cat /etc/init.d/99SolarInit.sh | grep -q 'enable-gpu-performance.sh'; then
    fail "99SolarInit.sh must enable GPU rendering + disable HW overlays on boot"
fi
if ! debugfs_cat /etc/init.d/99SolarInit.sh | grep -q 'SolarInputMethodService'; then
    fail "99SolarInit.sh must set default SolarInputMethodService"
fi
if ! debugfs_cat /etc/init.d/99SolarInit.sh | grep -q 'SolarImeAccessibilityService'; then
    fail "99SolarInit.sh must enable SolarImeAccessibilityService"
fi
if ! debugfs_cat /etc/install-recovery.sh | grep -q 'app_process.xposed.staged'; then
    fail "install-recovery.sh must apply staged Xposed app_process before zygote"
fi

ap="$tmpdir/app_process"
orig="$tmpdir/app_process.orig"
debugfs_dump /bin/app_process app_process || fail "could not dump app_process"
debugfs_dump /bin/app_process.orig app_process.orig || fail "could not dump app_process.orig"
if [ -f "$ap" ] && [ -f "$orig" ] && cmp -s "$ap" "$orig"; then
    fail "app_process identical to app_process.orig (Xposed not installed)"
fi
if [ -f "$ap" ] && ! strings "$ap" 2>/dev/null | grep -q 'with Xposed support'; then
    fail "app_process missing Xposed support string"
fi

# rockbox-y1 base ships permissive setuid su — stat Mode 06755 (debugfs ls -l on files fails).
su_mode=$(debugfs -R "stat /xbin/su" "$sys" 2>/dev/null \
    | sed -n 's/.*Mode:[[:space:]]*\([0-9]*\).*/\1/p' | head -1)
case "$su_mode" in
    06755|6755|4765) ;;
    *) fail "/system/xbin/su not setuid (mode=${su_mode:-?}, rockbox-y1 permissive root)" ;;
esac

canon="$tmpdir/Y1-Rockbox.kl"
debugfs_dump /etc/solar/Y1-Rockbox.kl Y1-Rockbox.kl || fail "could not read Y1-Rockbox.kl"
grep -qE '^key[[:space:]]+105[[:space:]]+MEDIA_PLAY' "$canon" \
    || fail "Y1-Rockbox.kl missing MEDIA_PLAY on scancode 105"

for kl in Generic.kl Stock.kl Rockbox.kl; do
    f="$tmpdir/$kl"
    debugfs_dump "/usr/keylayout/$kl" "$kl" || fail "missing /usr/keylayout/$kl"
    cmp -s "$f" "$canon" || fail "/usr/keylayout/$kl differs from Y1-Rockbox.kl"
done

bridge_apk="$tmpdir/SolarContextBridgeY1.apk"
debugfs_dump /app/SolarContextBridgeY1.apk SolarContextBridgeY1.apk \
    || fail "could not extract SolarContextBridgeY1.apk"
if ! unzip -p "$bridge_apk" classes.dex 2>/dev/null | strings | grep 'AppAnrHooks' >/dev/null; then
    fail "SolarContextBridgeY1.apk missing AppAnrHooks (rebuild build-context-bridge-apk.sh)"
fi
if ! unzip -p "$bridge_apk" classes.dex 2>/dev/null | strings | grep 'AppErrorHooks' >/dev/null; then
    fail "SolarContextBridgeY1.apk missing AppErrorHooks (rebuild build-context-bridge-apk.sh)"
fi

chmod +x "$SCRIPT_DIR/verify-rom-app-allowlist.sh"
"$SCRIPT_DIR/verify-rom-app-allowlist.sh" "$sys" || fail "system APK allowlist audit"

if [ "$errors" -ne 0 ]; then
    die "$errors check(s) failed — rebuild with ./solar-rom/scripts/build-rom.sh a|b"
fi

echo "==> verify-y1-rom-contents: OK (Rockbox-y1 APK, permissive su, Solar overlay)"
