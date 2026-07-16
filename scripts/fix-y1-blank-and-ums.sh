#!/usr/bin/env bash
# 2026-07-16 — Recover Y1 blank-screen (broken package path) + sticky UMS auto-export.
# Layman: put a complete Solar APK where Package Manager expects it, and stop auto disk mode.
# Usage: scripts/fix-y1-blank-and-ums.sh [SERIAL]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh" 2>/dev/null || true
SERIAL="${1:-${ANDROID_SERIAL:-SL246B8F23AB}}"
APK="${SOLAR_APK:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
ADB=(adb -s "$SERIAL")

if [[ ! -f "$APK" ]]; then
  echo "ERROR: missing APK at $APK — run: source scripts/env.sh && ./gradlew :app:assembleDebug" >&2
  exit 1
fi

echo "==> Waiting for device $SERIAL..."
"${ADB[@]}" wait-for-device
# Offline → wait for 'device'
for _ in $(seq 1 60); do
  st=$("${ADB[@]}" get-state 2>/dev/null | tr -d '\r' || true)
  [[ "$st" == "device" ]] && break
  sleep 2
done
st=$("${ADB[@]}" get-state 2>/dev/null | tr -d '\r' || true)
[[ "$st" == "device" ]] || { echo "ERROR: device state=$st (unplug/replug Y1)" >&2; exit 1; }

echo "==> Pushing APK ($(du -h "$APK" | awk '{print $1}'))..."
"${ADB[@]}" push "$APK" /data/local/tmp/com.solar.launcher.apk

echo "==> Device fix (APK path + clear sticky UMS)..."
"${ADB[@]}" shell "su 0 sh -s" <<'EOF'
set -e
am force-stop com.solar.launcher 2>/dev/null || true

# --- Sticky UMS off (never auto-present disks) ---
setprop persist.sys.usb.config adb
if [ -d /data/property ]; then
  echo -n adb > /data/property/persist.sys.usb.config 2>/dev/null || true
  chmod 600 /data/property/persist.sys.usb.config 2>/dev/null || true
fi
# Unbind LUNs only (do not flip android0/enable — that can drop adb)
for f in \
  /sys/class/android_usb/android0/f_mass_storage/lun/file \
  /sys/class/android_usb/android0/f_mass_storage/lun0/file \
  /sys/class/android_usb/android0/f_mass_storage/lun1/file; do
  [ -e "$f" ] && echo >"$f" 2>/dev/null || true
done
# Prefer adb if currently mass_storage (may re-enum; keep session alive if possible)
CUR="$(getprop sys.usb.config 2>/dev/null)"
case "$CUR" in
  *mass_storage*) setprop sys.usb.config adb ;;
esac

# --- Full APK as system + data path PackageManager expects ---
mount -o remount,rw /system 2>/dev/null || true
if [ -s /data/local/tmp/com.solar.launcher.apk ]; then
  cp -f /data/local/tmp/com.solar.launcher.apk /system/app/com.solar.launcher.apk
  chmod 644 /system/app/com.solar.launcher.apk
fi
# packages.xml often points at /data/app/com.solar.launcher-1.apk
cp -f /system/app/com.solar.launcher.apk /data/app/com.solar.launcher-1.apk
chmod 644 /data/app/com.solar.launcher-1.apk
chown system.system /data/app/com.solar.launcher-1.apk 2>/dev/null || chown system:system /data/app/com.solar.launcher-1.apk 2>/dev/null || true
rm -f /data/dalvik-cache/*com.solar.launcher* 2>/dev/null || true
sync

echo "PERSIST=$(getprop persist.sys.usb.config)"
echo "CONFIG=$(getprop sys.usb.config)"
echo "LUN=$(cat /sys/class/android_usb/android0/f_mass_storage/lun/file 2>/dev/null)"
echo "APK=$(ls -la /data/app/com.solar.launcher-1.apk | awk '{print $5}')"
echo FIX_OK
EOF

echo "==> Launch Solar..."
"${ADB[@]}" logcat -c 2>/dev/null || true
"${ADB[@]}" shell am force-stop com.solar.launcher 2>/dev/null || true
"${ADB[@]}" shell am start -n com.solar.launcher/.SolarLaunchActivity 2>&1 || \
  "${ADB[@]}" shell am start -n com.solar.launcher/.MainActivity 2>&1 || true
sleep 5
echo "==> Recent fatals / Solar boot:"
"${ADB[@]}" logcat -d -v time 2>/dev/null | grep -iE 'FATAL|ClassNotFound|SolarDebug|SolarLaunch|Application.onCreate' | tail -40 || true
echo "==> Done."
