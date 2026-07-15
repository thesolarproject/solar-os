#!/usr/bin/env bash
# 2026-07-14 — Push Solar A5 keylayouts (separate face DPAD vs side VOLUME).
# Layman: restores stock volume keys; power stays MEDIA_STOP so Solar maps it to Back.
# Tech: writes A5-mtk.kl → mtk-kpd.kl + A5.kl/Generic.kl; does NOT touch Y1/Y2 Rockbox.kl.
# Usage: ./scripts/push-a5-keymap.sh [--no-reboot]
# Reversal: restore from p2_ata_20230718/editor/system/usr/keylayout/ or ROM flash.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets/y1"
MTK="$ASSETS/A5-mtk.kl"
GEN="$ASSETS/A5.kl"
NO_REBOOT=0
for arg in "$@"; do
  [[ "$arg" == "--no-reboot" ]] && NO_REBOOT=1
done
[[ -f "$MTK" ]] || { echo "missing $MTK" >&2; exit 1; }
[[ -f "$GEN" ]] || { echo "missing $GEN" >&2; exit 1; }

adb wait-for-device
if [[ -z "${ANDROID_SERIAL:-}" ]] && [[ "$(adb devices | awk 'NR>1 && $2=="device"{print $1}' | sort -u | wc -l)" -gt 1 ]]; then
  echo "Multiple devices — set ANDROID_SERIAL to the A5" >&2
  adb devices -l
  exit 1
fi
ADB=(adb)
[[ -n "${ANDROID_SERIAL:-}" ]] && ADB=(adb -s "$ANDROID_SERIAL")

echo "== Push A5 keymaps =="
"${ADB[@]}" shell getprop persist.solar.device_family || true
"${ADB[@]}" push "$MTK" /data/local/tmp/A5-mtk.kl
"${ADB[@]}" push "$GEN" /data/local/tmp/A5.kl
"${ADB[@]}" shell "su -c '
mount -o remount,rw /system
mkdir -p /system/etc/solar /system/usr/keylayout
cp /data/local/tmp/A5-mtk.kl /system/etc/solar/A5-mtk.kl
cp /data/local/tmp/A5.kl /system/etc/solar/A5.kl
# Keypad device name is mtk-kpd — restore Solar A5 map (stock + power MEDIA_STOP).
cp /data/local/tmp/A5-mtk.kl /system/usr/keylayout/mtk-kpd.kl
# Fix corrupted Generic/A5 that mapped VOLUME scancodes to DPAD on some units.
cp /data/local/tmp/A5.kl /system/usr/keylayout/A5.kl
cp /data/local/tmp/A5.kl /system/usr/keylayout/Generic.kl
chmod 644 /system/usr/keylayout/mtk-kpd.kl /system/usr/keylayout/A5.kl /system/usr/keylayout/Generic.kl
chmod 644 /system/etc/solar/A5-mtk.kl /system/etc/solar/A5.kl
echo \"mtk-kpd:\"
grep -E \"^key (103|108|114|115|116|158)\" /system/usr/keylayout/mtk-kpd.kl
echo \"Generic:\"
grep -E \"^key (103|108|114|115|116|158)\" /system/usr/keylayout/Generic.kl
'"

# Keep family pin across reboot
"${ADB[@]}" shell "su -c 'setprop persist.solar.device_family a5'" 2>/dev/null \
  || "${ADB[@]}" shell setprop persist.solar.device_family a5 || true

if [[ "$NO_REBOOT" -eq 1 ]]; then
  echo "Skipped reboot (--no-reboot). Reboot later so InputReader reloads."
  exit 0
fi
echo "Rebooting so InputReader reloads keylayout..."
"${ADB[@]}" reboot
