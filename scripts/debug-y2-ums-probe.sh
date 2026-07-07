#!/usr/bin/env bash
# 2026-07-05 — Y2 UMS host+adb probe; writes NDJSON to workspace debug log (session a3510d).
# Layman: checks whether the PC sees a USB disk and whether the phone agrees on disk mode.
# Usage: ./scripts/debug-y2-ums-probe.sh [adb-serial]

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG="$ROOT/.cursor/debug-a3510d.log"
TS="$(date +%s)000"
RUN="${RUN_ID:-pre-fix}"

Y2="${1:-}"
if [ -z "$Y2" ]; then
  Y2="$(adb devices -l 2>/dev/null | awk '/product:Y2/{print $1; exit}')"
fi
if [ -z "$Y2" ]; then
  echo "No Y2 adb device found" >&2
  exit 1
fi

ADB=(adb -s "$Y2")

write_json() {
  local loc="$1" msg="$2" hyp="$3" data="$4"
  printf '{"sessionId":"a3510d","runId":"%s","timestamp":%s,"location":"%s","message":"%s","hypothesisId":"%s","data":{%s}}\n' \
    "$RUN" "$TS" "$loc" "$msg" "$hyp" "$data" >>"$LOG"
}

usb_prop="$("${ADB[@]}" shell getprop sys.usb.config 2>/dev/null | tr -d '\r')"
kern_fn="$("${ADB[@]}" shell cat /sys/class/android_usb/android0/functions 2>/dev/null | tr -d '\r')"
dumpsys_usb="$("${ADB[@]}" shell dumpsys usb 2>/dev/null | tr -d '\r' | head -c 2000)"
lun0="$("${ADB[@]}" shell cat /sys/class/android_usb/android0/f_mass_storage/lun0/file 2>/dev/null | tr -d '\r' || true)"
lun_file="$("${ADB[@]}" shell cat /sys/class/android_usb/android0/f_mass_storage/lun/file 2>/dev/null | tr -d '\r' || true)"
backing="$(echo "$dumpsys_usb" | grep -o 'Mass storage backing file: [^$]*' | sed 's/Mass storage backing file: //' || true)"
cur_fn="$(echo "$dumpsys_usb" | grep -o 'Current Functions: [^$]*' | sed 's/Current Functions: //' || true)"
vdc_list="$("${ADB[@]}" shell vdc volume list 2>/dev/null | tr -d '\r' || true)"

# PC-side: MTK/Android phones often show 0bb4:0c03 (MTP); mass storage adds scsi/block nodes.
lsusb_y2="$(lsusb 2>/dev/null | grep -E '0bb4:0c03|18d1:4ee|Mass Storage|Android' || true)"
block_devs="$(ls -1 /dev/disk/by-id/ 2>/dev/null | grep -iE 'usb|android|mediatek|0bb4' || true)"
lsblk_usb="$(lsblk -o NAME,TRAN,RM,SIZE,MODEL 2>/dev/null | grep -i usb || true)"

write_json "host.debug-y2-ums-probe" "adb usb state" "H1,H2" \
  "\"serial\":\"$Y2\",\"usbProp\":\"$usb_prop\",\"kernelFn\":\"$kern_fn\",\"currentFunctions\":\"$cur_fn\",\"backing\":\"$backing\",\"lun0\":\"$lun0\",\"lunFile\":\"$lun_file\""

write_json "host.debug-y2-ums-probe" "vdc volumes" "H3" \
  "\"vdcList\":\"$(echo "$vdc_list" | sed 's/"/\\"/g')\""

write_json "host.debug-y2-ums-probe" "pc usb visibility" "H5" \
  "\"lsusb\":\"$(echo "$lsusb_y2" | sed 's/"/\\"/g' | tr '\n' ';')\",\"diskById\":\"$(echo "$block_devs" | sed 's/"/\\"/g' | tr '\n' ';')\",\"lsblkUsb\":\"$(echo "$lsblk_usb" | sed 's/"/\\"/g' | tr '\n' ';')\""

# Pull device-side debug log tail if present
dev_log="$("${ADB[@]}" shell cat /data/local/tmp/debug-a3510d.log 2>/dev/null | tail -5 | tr -d '\r' || true)"
if [ -n "$dev_log" ]; then
  write_json "host.debug-y2-ums-probe" "device debug tail" "H2,H4" \
    "\"tail\":\"$(echo "$dev_log" | sed 's/"/\\"/g' | tr '\n' ';')\""
fi

echo "Probe written to $LOG (Y2=$Y2 prop=$usb_prop kernel=$kern_fn current=$cur_fn)"
