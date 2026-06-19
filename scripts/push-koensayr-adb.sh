#!/usr/bin/env bash
# Push Koensayr-patched system files to a rooted Y1 via adb + su (no adb root required).
# Usage: push-koensayr-adb.sh [--no-reboot]
set -euo pipefail

NO_REBOOT=0
[[ "${1:-}" == "--no-reboot" ]] && NO_REBOOT=1

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh"

STAGE_SCRIPT="$ROOT/scripts/stage-koensayr-prep.sh"
INIT_SRC="$ROOT/solar-rom/system/99SolarInit.sh"
BACKUP_ROOT="$ROOT/backups/koensayr-$(date +%Y%m%d-%H%M%S)"

die() { echo "error: $*" >&2; exit 1; }

run_su() {
  adb shell "su -c '$*'" 2>/dev/null || adb shell "$*"
}

KOENSAYR_PUSH_PATHS=(
    app/Y1Bridge.apk
    app/MtkBt.odex
    bin/mtkbt
    lib/libextavrcp_jni.so
    lib/libextavrcp.so
    lib/libaudio.a2dp.default.so
    usr/keylayout/AVRCP.kl
    etc/bluetooth/audio.conf
    etc/bluetooth/auto_pairing.conf
    etc/bluetooth/blacklist.conf
    build.prop
)

echo "== Waiting for device (120s) =="
timeout 120 adb wait-for-device || die "no adb device"
sleep 2

echo "==> Staging Koensayr patches"
chmod +x "$STAGE_SCRIPT" "$ROOT/solar-rom/scripts/koensayr-apply-to-tree.sh"
STAGING="$ROOT/build/koensayr-adb-staging/system"
"$STAGE_SCRIPT" "$STAGING"
[[ -f "$STAGING/app/Y1Bridge.apk" ]] || die "staging failed"

echo "==> Backing up live /system files to $BACKUP_ROOT"
mkdir -p "$BACKUP_ROOT"
for rel in "${KOENSAYR_PUSH_PATHS[@]}"; do
    if adb shell "test -f /system/$rel" 2>/dev/null; then
        mkdir -p "$BACKUP_ROOT/$(dirname "$rel")"
        echo "  backup /system/$rel"
        adb pull "/system/$rel" "$BACKUP_ROOT/$rel" >/dev/null 2>&1 || true
    fi
done

echo "== Root + remount /system =="
adb root 2>/dev/null || true
sleep 1
adb remount 2>/dev/null || run_su "mount -o remount,rw /system" || die "cannot remount /system"

push_file() {
    local rel="$1"
    local mode="$2"
    local src="$STAGING/$rel"
    [[ -f "$src" ]] || { echo "  skip (not staged): $rel"; return 0; }
    local tmp="/data/local/tmp/solar-koensayr-$(basename "$rel")"
    echo "  push /system/$rel"
    adb push "$src" "$tmp"
    run_su "cp $tmp /system/$rel && chmod $mode /system/$rel && chown root:root /system/$rel && rm -f $tmp"
}

echo "==> Push Koensayr files"
push_file app/Y1Bridge.apk 644
push_file app/MtkBt.odex 644
push_file bin/mtkbt 755
push_file lib/libextavrcp_jni.so 644
push_file lib/libextavrcp.so 644
push_file lib/libaudio.a2dp.default.so 644
push_file usr/keylayout/AVRCP.kl 644
push_file etc/bluetooth/audio.conf 644
push_file etc/bluetooth/auto_pairing.conf 644
push_file etc/bluetooth/blacklist.conf 644

if ! adb shell "grep -q avrcp.target.enabled /system/build.prop" 2>/dev/null; then
    echo "  append Koensayr build.prop lines"
    run_su "printf '%s\n' '' '# Solar / Koensayr AVRCP target profile' 'ro.bluetooth.class=10486812' 'ro.bluetooth.profiles.a2dp.source.enabled=true' 'ro.bluetooth.profiles.avrcp.target.enabled=true' >> /system/build.prop" || true
fi

if [[ -f "$INIT_SRC" ]] && ! adb shell "test -f /system/etc/init.d/99SolarInit.sh" 2>/dev/null; then
    echo "==> Push 99SolarInit.sh"
    adb push "$INIT_SRC" /data/local/tmp/99SolarInit.sh
    run_su "mkdir -p /system/etc/init.d && cp /data/local/tmp/99SolarInit.sh /system/etc/init.d/99SolarInit.sh && chmod 755 /system/etc/init.d/99SolarInit.sh && chown root:root /system/etc/init.d/99SolarInit.sh && rm -f /data/local/tmp/99SolarInit.sh"
fi

run_su "sync"

echo "==> Verify"
adb shell "ls -la /system/app/Y1Bridge.apk /system/lib/libextavrcp_jni.so 2>/dev/null" || true
adb shell pm list packages 2>/dev/null | grep -i bridge || true

if [[ "$NO_REBOOT" -eq 0 ]]; then
    echo "==> Rebooting (BT stack loads patched libs at boot)"
    adb reboot
    echo "DONE: Koensayr pushed — device rebooting"
else
    echo "DONE: Koensayr pushed (no reboot — run adb reboot when ready)"
fi
