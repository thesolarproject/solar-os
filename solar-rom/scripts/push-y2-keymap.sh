#!/usr/bin/env bash
# Push Solar Y2 unified keymap to a rooted Y2 (no full ROM reflash).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCR="$ROOT/solar-rom/scripts"
SERIAL="${ANDROID_SERIAL:-SLCAFDFA9A42}"
ADB=(adb -s "$SERIAL")
"${ADB[@]}" wait-for-device
"${ADB[@]}" push "$SCR/Y2-Rockbox.kl" /data/local/tmp/Y2-Rockbox.kl
"${ADB[@]}" push "$SCR/sync-y1-keymap.sh" /data/local/tmp/sync-y1-keymap.sh
"${ADB[@]}" shell "su -c 'mount -o remount,rw /system
mkdir -p /system/etc/solar
cp /data/local/tmp/Y2-Rockbox.kl /system/etc/solar/Y2-Rockbox.kl
cp /data/local/tmp/sync-y1-keymap.sh /system/etc/solar/sync-y1-keymap.sh
chmod 644 /system/etc/solar/Y2-Rockbox.kl
chmod 755 /system/etc/solar/sync-y1-keymap.sh
ln -sf /system/xbin/su /system/bin/su 2>/dev/null || cp /system/xbin/su /system/bin/su
chmod 6755 /system/bin/su 2>/dev/null || true
sh /system/etc/solar/sync-y1-keymap.sh
echo \"identical check:\"
cmp /system/usr/keylayout/Generic.kl /system/usr/keylayout/Stock.kl && echo Generic=Stock
cmp /system/usr/keylayout/Stock.kl /system/usr/keylayout/Rockbox.kl && echo Stock=Rockbox
cmp /system/usr/keylayout/Rockbox.kl /system/usr/keylayout/mtk-kpd.kl && echo Rockbox=mtk-kpd
cmp /system/usr/keylayout/mtk-kpd.kl /system/usr/keylayout/mtk-tpd-kpd.kl && echo mtk-kpd=mtk-tpd-kpd'"
echo "Rebooting so InputReader reloads keylayout..."
"${ADB[@]}" reboot
