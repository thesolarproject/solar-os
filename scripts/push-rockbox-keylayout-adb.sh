#!/usr/bin/env bash
# Push canonical Rockbox-Y1 mtk-kpd to a rooted Y1 (wheel 19/20, prev/next 21/22).
# Usage: push-rockbox-keylayout-adb.sh [--no-reboot]
set -euo pipefail

NO_REBOOT=0
[[ "${1:-}" == "--no-reboot" ]] && NO_REBOOT=1

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh"

KL="$ROOT/solar-rom/scripts/mtk-kpd-rockbox.kl"
STOCK_KL="$ROOT/solar-rom/scripts/Stock.kl"

die() { echo "error: $*" >&2; exit 1; }

run_su() {
  adb shell "su -c '$*'" 2>/dev/null || adb shell "$*"
}

[[ -f "$KL" ]] || die "missing $KL"

echo "== Waiting for device (120s) =="
timeout 120 adb wait-for-device || die "no adb device"
sleep 2

echo "== Root + remount /system =="
adb root 2>/dev/null || true
sleep 1
adb remount 2>/dev/null || run_su "mount -o remount,rw /system" || die "cannot remount /system"

echo "==> Push Rockbox keylayout (Generic.kl + Stock.kl + mtk-kpd-rockbox.kl)"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
cp "$STOCK_KL" "$STAGE/Stock.kl"
cp "$STOCK_KL" "$STAGE/Generic.kl"
chmod +x "$ROOT/solar-rom/scripts/patch-kl-rockbox-y1-controls.sh"
"$ROOT/solar-rom/scripts/patch-kl-rockbox-y1-controls.sh" "$STAGE/Stock.kl" "$STAGE/Generic.kl"
cp "$KL" "$STAGE/mtk-kpd.kl"
adb push "$STAGE/Stock.kl" /data/local/tmp/Stock.kl
adb push "$STAGE/Generic.kl" /data/local/tmp/Generic.kl
adb push "$STAGE/mtk-kpd.kl" /data/local/tmp/mtk-kpd.kl
run_su "mkdir -p /system/usr/keylayout && cp /data/local/tmp/Stock.kl /system/usr/keylayout/Stock.kl && cp /data/local/tmp/Generic.kl /system/usr/keylayout/Generic.kl && cp /data/local/tmp/mtk-kpd.kl /system/usr/keylayout/mtk-kpd.kl && rm -f /system/usr/keylayout/Rockbox.kl && chmod 644 /system/usr/keylayout/Stock.kl /system/usr/keylayout/Generic.kl /system/usr/keylayout/mtk-kpd.kl && chown root:root /system/usr/keylayout/Stock.kl /system/usr/keylayout/Generic.kl /system/usr/keylayout/mtk-kpd.kl && rm -f /data/local/tmp/Stock.kl /data/local/tmp/Generic.kl /data/local/tmp/mtk-kpd.kl"
run_su "sync"

echo "==> Verify mtk-kpd + Generic.kl scancodes 103–106"
adb shell "grep -E '^key (103|105|106|108)' /system/usr/keylayout/mtk-kpd.kl" | tr -d '\r' || true
adb shell "grep -E '^key (103|105|106|108)' /system/usr/keylayout/Generic.kl" | tr -d '\r' || true

if [[ "$NO_REBOOT" -eq 0 ]]; then
    echo "==> Rebooting (InputReader reloads keylayout at boot)"
    adb reboot
    echo "DONE: Rockbox mtk-kpd installed — device rebooting"
else
    echo "DONE: Rockbox mtk-kpd installed (no reboot — run adb reboot when ready)"
fi
