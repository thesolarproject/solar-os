#!/usr/bin/env bash
# Post-zip Y2 ROM audit — catches unpatchable Rockbox / keylayout regressions CI must block.
# Usage: verify-y2-rom-contents.sh [rom_y2.zip]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib-android-tools.sh
source "$SCRIPT_DIR/lib-android-tools.sh"

ZIP="${1:-rom_y2.zip}"
die() { echo "verify-y2-rom-contents: error: $*" >&2; exit 1; }

[ -f "$ZIP" ] || die "usage: $0 [rom_y2.zip]"
command -v unzip >/dev/null 2>&1 || die "missing unzip"
command -v debugfs >/dev/null 2>&1 || die "missing debugfs (e2fsprogs)"

AAPT="$(find_android_build_tool aapt)" || die "missing aapt (set ANDROID_HOME or add build-tools to PATH)"

SOLAR_ROM_BUILD_DIR="${SOLAR_ROM_BUILD_DIR:-$HOME/.cache/solar-rom-build}"
mkdir -p "$SOLAR_ROM_BUILD_DIR"
tmpdir=$(mktemp -d "$SOLAR_ROM_BUILD_DIR/verify-y2-XXXXXX")
trap 'rm -rf "$tmpdir"' EXIT

echo "==> verify-y2-rom-contents: $ZIP"
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
    # ls -l works on directories; files need stat (debugfs ls on a file errors).
    if debugfs -R "stat $1" "$sys" 2>/dev/null | grep -q 'Type: regular'; then
        return 0
    fi
    debugfs -R "ls -l $1" "$sys" 2>/dev/null | grep -q . \
        || die "missing $1 in system.img"
}

errors=0
fail() { echo "verify-y2-rom-contents: FAIL: $*" >&2; errors=$((errors + 1)); }

# Solar launcher + patched Rockbox (Y2-only install path in build-rom.sh).
require_path /app/com.solar.launcher.apk || fail "/app/com.solar.launcher.apk"
require_path /app/org.rockbox.apk || fail "/app/org.rockbox.apk"
require_path /lib/librockbox.so || fail "/lib/librockbox.so"
require_path /etc/solar/rockbox-libs/librockbox.so || fail "/etc/solar/rockbox-libs/librockbox.so"

rb_apk="$tmpdir/org.rockbox.apk"
debugfs_dump /app/org.rockbox.apk org.rockbox.apk || fail "could not extract org.rockbox.apk"
[ -s "$rb_apk" ] || fail "org.rockbox.apk empty"

if "$AAPT" dump xmltree "$rb_apk" AndroidManifest.xml 2>/dev/null | grep -q 'sharedUserId'; then
    fail "org.rockbox.apk still has sharedUserId (patch-rockbox-y2.sh did not run in ROM build)"
fi

# grep -q + pipefail makes unzip SIGPIPE — count libmisc lines instead.
rb_misc_count=$(unzip -l "$rb_apk" 2>/dev/null | grep -c 'lib/armeabi/libmisc.so' || true)
[ "${rb_misc_count:-0}" -ge 1 ] || fail "org.rockbox.apk missing lib/armeabi/libmisc.so"

# Launcher switch + keymap scripts (Rockbox-Y1 handoff parity).
for p in \
    /etc/solar/switch-to-stock.sh \
    /etc/solar/switch-to-rockbox.sh \
    /etc/solar/sync-y1-keymap.sh \
    /etc/solar/sync-rockbox-libs.sh \
    /etc/solar/sync-rockbox-assets.sh \
    /etc/solar/disable-rockbox-for-solar.sh \
    /etc/solar/apply-preferred-home-boot.sh \
    /etc/solar/Y2-Rockbox.kl \
    /etc/init.d/99SolarInit.sh \
    /etc/init.d/99Y1ButtonScript \
    /etc/init.d/99SuperSUDaemon \
    /etc/install-recovery.sh \
    /etc/install-recovery-2.sh \
    /etc/.installed_su_daemon; do
    require_path "$p" || fail "missing $p"
done

if ! debugfs_cat /etc/init.d/99SolarInit.sh | grep -q 'apply-preferred-home-boot.sh'; then
    fail "99SolarInit.sh must run apply-preferred-home-boot.sh"
fi
if ! debugfs_cat /etc/init.d/99SolarInit.sh | grep -q 'daemonsu'; then
    fail "99SolarInit.sh must start daemonsu for app su -c"
fi
if ! debugfs_cat /etc/install-recovery.sh | grep -q 'daemonsu'; then
    fail "install-recovery.sh must start daemonsu (init.rc flash_recovery hook)"
fi

# Unified keylayout playbook — wheel MEDIA on 103/108, identical mtk mirrors.
canon="$tmpdir/Y2-Rockbox.kl"
debugfs_dump /etc/solar/Y2-Rockbox.kl Y2-Rockbox.kl || fail "could not read Y2-Rockbox.kl"
grep -qE '^key[[:space:]]+103[[:space:]]+MEDIA_PLAY' "$canon" \
    || fail "Y2-Rockbox.kl missing MEDIA_PLAY on scancode 103"
grep -qE '^key[[:space:]]+108[[:space:]]+MEDIA_PAUSE' "$canon" \
    || fail "Y2-Rockbox.kl missing MEDIA_PAUSE on scancode 108"
# Y2 side buttons are 105/106 — must NOT use Y1 wheel map (105/106 = MEDIA_PLAY/PAUSE).
grep -qE '^key[[:space:]]+105[[:space:]]+MEDIA_PREVIOUS' "$canon" \
    || fail "Y2-Rockbox.kl missing MEDIA_PREVIOUS on scancode 105 (Y1 wheel map baked in?)"
grep -qE '^key[[:space:]]+106[[:space:]]+MEDIA_NEXT' "$canon" \
    || fail "Y2-Rockbox.kl missing MEDIA_NEXT on scancode 106"
grep -qE '^key[[:space:]]+114[[:space:]]+VOLUME_DOWN' "$canon" \
    || fail "Y2-Rockbox.kl missing VOLUME_DOWN on scancode 114"
grep -qE '^key[[:space:]]+115[[:space:]]+VOLUME_UP' "$canon" \
    || fail "Y2-Rockbox.kl missing VOLUME_UP on scancode 115"
grep -qE '^key[[:space:]]+116[[:space:]]+POWER' "$canon" \
    || fail "Y2-Rockbox.kl missing POWER on scancode 116"

for kl in Generic.kl Stock.kl Rockbox.kl mtk-kpd.kl mtk-tpd-kpd.kl; do
    f="$tmpdir/$kl"
    debugfs_dump "/usr/keylayout/$kl" "$kl" || fail "missing /usr/keylayout/$kl"
    cmp -s "$f" "$canon" || fail "/usr/keylayout/$kl differs from Y2-Rockbox.kl"
done

# debugfs ls -l on a file path errors — read Mode from stat (06755 = setuid root).
assert_setuid_su() {
    local path="$1"
    require_path "$path" || fail "$path missing"
    local mode
    mode=$(debugfs -R "stat $path" "$sys" 2>/dev/null \
        | sed -n 's/.*Mode:[[:space:]]*\([0-9]*\).*/\1/p' | head -1)
    case "$mode" in
        06755|6755|4765) return 0 ;;
        *) fail "$path not setuid (mode=${mode:-?}, expected 6755 from install-y1-su-system.sh)" ;;
    esac
}
assert_setuid_su /xbin/su
assert_setuid_su /bin/su
assert_setuid_su /xbin/daemonsu
assert_setuid_su /bin/.ext/.su

# Xposed modules baked into Y2 ROM (API 19 framework — verify-xposed-rom-contents.sh also audits these).
for p in \
    /framework/XposedBridge.jar \
    /xposed.prop \
    /app/XposedInstaller.apk \
    /app/SolarContextBridgeY2.apk \
    /app/SolarThemeFont.apk \
    /etc/init.d/99XposedInit.sh; do
    require_path "$p" || fail "missing $p"
done
if ! debugfs_cat /etc/init.d/99XposedInit.sh | grep -q 'com.solar.launcher.xposed.themefont'; then
    fail "99XposedInit.sh must enable com.solar.launcher.xposed.themefont"
fi
if ! debugfs_cat /etc/init.d/99XposedInit.sh | grep -q 'com.solar.launcher.xposed.bridge.y2'; then
    fail "99XposedInit.sh must enable com.solar.launcher.xposed.bridge.y2"
fi

# Staged Rockbox assets (Y2 dual-storage + db_folder_select patch).
require_path /etc/solar/rockbox-y2-config.cfg || fail "rockbox-y2-config.cfg"
require_path /etc/solar/rockbox-libs/librockbox.so || fail "staged rockbox-libs"
require_path /etc/solar/rockbox-dot-rockbox/rocks/viewers/db_folder_select.rock \
    || fail "staged db_folder_select.rock"

if [ "$errors" -ne 0 ]; then
    die "$errors check(s) failed — rebuild with ./solar-rom/scripts/build-rom.sh y2"
fi

echo "==> verify-y2-rom-contents: OK (patched Rockbox, keylayout, switch scripts, root)"
