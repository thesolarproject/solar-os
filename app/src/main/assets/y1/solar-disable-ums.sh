#!/system/bin/sh
# Solar USB mass storage disable (2026-07-17).
# Layman: stop sharing disks with the PC, then put Music back on the player.
# Tech: vdc unshare leaves volumes Idle-Unmounted; without an explicit mount the
# library is empty (All Songs lists nothing) until reboot. Always remount.
# Y1: unshare + USB=adb only (no MTP — PC uses disk mode when user turns UMS on).
# Y2: unshare + USB=mtp,adb (product uses MTP; UMS not supported).

MODEL="$(getprop ro.product.model 2>/dev/null)"
# Unshare every known export path (Y1 often has both sdcard0 and sdcard1).
VOLS="/storage/sdcard0 /storage/sdcard1"
USB_AFTER="adb"
case "$MODEL" in
  Y2|*Y2*)
    USB_AFTER="mtp,adb"
    ;;
esac

for vol in $VOLS; do
  vdc volume unshare "$vol" ums 2>/dev/null
done
# Short settle so LUN clear + setprop are not raced by remount.
sleep 0.12
# Always clear LUN nodes before mode switch — stale lun/file caused false “USB storage on”.
for lun in \
    /sys/class/android_usb/android0/f_mass_storage/lun/file \
    /sys/class/android_usb/android0/f_mass_storage/lun0/file \
    /sys/class/android_usb/android0/f_mass_storage/lun1/file \
    /sys/devices/platform/mt_usb/gadget/lun0/file \
    /sys/devices/platform/mt_usb/gadget/lun1/file; do
  if [ -w "$lun" ]; then
    echo >"$lun" 2>/dev/null
  fi
done
setprop sys.usb.config "$USB_AFTER"
# Never leave mass_storage sticky across reboot/reconnect.
# Tech: MediaTek UsbDeviceManager copies sys.usb.config → persist.sys.usb.config on enable.
setprop persist.sys.usb.config "$USB_AFTER"
if [ -d /data/property ]; then
  echo -n "$USB_AFTER" > /data/property/persist.sys.usb.config 2>/dev/null
  chmod 600 /data/property/persist.sys.usb.config 2>/dev/null
fi

# Remount so apps can see Music again. Unshare alone leaves Idle-Unmounted.
sleep 0.25
for vol in $VOLS; do
  if grep -q " $vol " /proc/mounts 2>/dev/null; then
    continue
  fi
  i=0
  while [ "$i" -lt 8 ]; do
    vdc volume mount "$vol" 2>/dev/null
    if grep -q " $vol " /proc/mounts 2>/dev/null; then
      break
    fi
    if [ "$i" -lt 3 ]; then
      sleep 0.25
    else
      sleep 0.5
    fi
    i=$((i + 1))
  done
done

mounted=0
for vol in $VOLS; do
  if grep -q " $vol " /proc/mounts 2>/dev/null; then
    mounted=$((mounted + 1))
  fi
done
echo "UMS disabled usb=$USB_AFTER persist=$USB_AFTER mounted=$mounted"
exit 0
