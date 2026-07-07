#!/system/bin/sh
# Solar USB mass storage disable — unshare volumes then restore MTP+adb (2026-07-05).
# Layman: stop sharing disks with the PC and return USB to normal MTP+adb for adb/MTP tools.
# Y1: unshare sdcard0. Y2: unshare internal sdcard0 + MicroSD sdcard1.

MODEL="$(getprop ro.product.model 2>/dev/null)"
VOLS="/storage/sdcard0"
case "$MODEL" in
  Y2|*Y2*) VOLS="/storage/sdcard0 /storage/sdcard1" ;;
esac

for vol in $VOLS; do
  vdc volume unshare "$vol" ums 2>/dev/null
done
sleep 0.5
setprop sys.usb.config mtp,adb
# Clear stale lun/file nodes — sysfs can retain a path after MTP restore (2026-07-05).
for lun in \
    /sys/class/android_usb/android0/f_mass_storage/lun/file \
    /sys/class/android_usb/android0/f_mass_storage/lun0/file \
    /sys/class/android_usb/android0/f_mass_storage/lun1/file; do
  if [ -w "$lun" ]; then
    echo >"$lun" 2>/dev/null
  fi
done
echo "UMS disabled"
exit 0
