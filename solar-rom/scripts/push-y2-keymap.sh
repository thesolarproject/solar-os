#!/usr/bin/env bash
# Push Solar Y2 coexistence keymap to a rooted Y2 (no full ROM reflash).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCR="$ROOT/solar-rom/scripts"
SERIAL="${ANDROID_SERIAL:-0123456789ABCDEF}"
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
sh /system/etc/solar/sync-y1-keymap.sh
echo \"Generic wheel:\"
grep -E \"^key (105|106|114|115|163|165)\" /system/usr/keylayout/Generic.kl
echo \"mtk-kpd side + wheel:\"
grep -E \"^key (105|106|114|115|163|165)\" /system/usr/keylayout/mtk-kpd.kl'"
echo "Rebooting so InputReader reloads keylayout..."
"${ADB[@]}" reboot
