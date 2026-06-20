#!/usr/bin/env bash
# Push rockbox-y1 Rockbox.kl + mtk-kpd to a rooted Y1.
# Usage: push-rockbox-keylayout-adb.sh [--no-reboot]
set -euo pipefail

NO_REBOOT=0
[[ "${1:-}" == "--no-reboot" ]] && NO_REBOOT=1

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh"

ROCKBOX_KL="$ROOT/solar-rom/scripts/Rockbox.kl"
MTK_KL="$ROOT/solar-rom/scripts/mtk_kpd.kl"

die() { echo "error: $*" >&2; exit 1; }

run_su() {
  adb shell su 0 "$@"
}

[[ -f "$ROCKBOX_KL" ]] || die "missing $ROCKBOX_KL"
[[ -f "$MTK_KL" ]] || die "missing $MTK_KL"

echo "== Waiting for device (120s) =="
timeout 120 adb wait-for-device || die "no adb device"
sleep 2

echo "== Root + remount /system =="
adb shell su 0 id 2>/dev/null | tr -d '\r' | grep -q 'uid=0' \
  || die "su root required to write /system/usr/keylayout"
adb root 2>/dev/null || true
sleep 1
run_su mount -o remount,rw /system || die "cannot remount /system"

echo "==> Push Rockbox keylayout (Rockbox.kl + mtk_kpd.kl)"
adb push "$ROCKBOX_KL" /data/local/tmp/Rockbox.kl
adb push "$ROCKBOX_KL" /data/local/tmp/Stock.kl
adb push "$ROCKBOX_KL" /data/local/tmp/Generic.kl
adb push "$MTK_KL" /data/local/tmp/mtk_kpd.kl
adb push "$ROOT/solar-rom/scripts/mtk-kpd-rockbox.kl" /data/local/tmp/mtk-kpd.kl
run_su mkdir -p /system/usr/keylayout
run_su cp /data/local/tmp/Stock.kl /system/usr/keylayout/Stock.kl
run_su cp /data/local/tmp/Generic.kl /system/usr/keylayout/Generic.kl
run_su cp /data/local/tmp/Rockbox.kl /system/usr/keylayout/Rockbox.kl
run_su cp /data/local/tmp/mtk-kpd.kl /system/usr/keylayout/mtk-kpd.kl
run_su cp /data/local/tmp/mtk_kpd.kl /system/usr/keylayout/mtk_kpd.kl
run_su chmod 644 /system/usr/keylayout/Stock.kl /system/usr/keylayout/Generic.kl /system/usr/keylayout/Rockbox.kl /system/usr/keylayout/mtk-kpd.kl /system/usr/keylayout/mtk_kpd.kl
run_su chown root:root /system/usr/keylayout/Stock.kl /system/usr/keylayout/Generic.kl /system/usr/keylayout/Rockbox.kl /system/usr/keylayout/mtk-kpd.kl /system/usr/keylayout/mtk_kpd.kl
run_su rm -f /data/local/tmp/Stock.kl /data/local/tmp/Generic.kl /data/local/tmp/Rockbox.kl /data/local/tmp/mtk-kpd.kl /data/local/tmp/mtk_kpd.kl
run_su sync

G103="$(adb shell su 0 cat /system/usr/keylayout/Generic.kl 2>/dev/null | tr -d '\r' | grep '^key 103' | head -1)"
if [[ "$G103" != *MEDIA_PLAY* ]]; then
  die "Generic.kl still not rockbox-y1 Rockbox.kl after su cp (got: ${G103:-missing})"
fi

echo "==> Verify Generic.kl (only keylayout loaded for mtk-kpd on Y1 Android 4.2)"
adb shell su 0 cat /system/usr/keylayout/Generic.kl 2>/dev/null | tr -d '\r' | grep -E '^key (103|105|106|108|114|115)' || true

if [[ "$NO_REBOOT" -eq 0 ]]; then
    echo "==> Rebooting (InputReader reloads keylayout at boot)"
    adb reboot
    echo "DONE: Rockbox keylayout installed — device rebooting"
else
    echo "DONE: Rockbox keylayout installed (no reboot — run adb reboot when ready)"
fi
