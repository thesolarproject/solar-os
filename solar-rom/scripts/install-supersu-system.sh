#!/usr/bin/env bash
# Bake SuperSU v2.76 system-root into a loop-mounted /system image (KitKat armv7).
# Mirrors the "system install" paths from META-INF/com/google/android/update-binary.
set -euo pipefail

MOUNT="${1:?usage: install-supersu-system.sh MOUNTED_SYSTEM_DIR}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENDOR="$SCRIPT_DIR/../vendor/supersu"
BIN="$VENDOR/armv7"
COM="$VENDOR/common"

die() {
    echo "error: $*" >&2
    exit 1
}

[ -d "$MOUNT/xbin" ] || die "not a system mount: $MOUNT"
[ -f "$BIN/su" ] || die "missing $BIN/su (vendor SuperSU armv7 binaries)"
[ -f "$COM/Superuser.apk" ] || die "missing $COM/Superuser.apk"
[ -f "$MOUNT/bin/sh" ] || die "stock /system/bin/sh missing (needed for sugote-mksh)"

echo "==> Install SuperSU v2.76 (system mode, armv7 KitKat)"

# su, daemonsu, sugote share the same pie binary on API 17–21.
sudo mkdir -p "$MOUNT/bin/.ext" "$MOUNT/xbin" "$MOUNT/app" "$MOUNT/etc/init.d"
sudo chmod 0777 "$MOUNT/bin/.ext"

sudo cp "$BIN/su" "$MOUNT/bin/.ext/.su"
sudo cp "$BIN/su" "$MOUNT/xbin/su"
sudo cp "$BIN/su" "$MOUNT/xbin/daemonsu"
sudo cp "$BIN/su" "$MOUNT/xbin/sugote"
sudo chmod 0755 "$MOUNT/bin/.ext/.su" "$MOUNT/xbin/su" "$MOUNT/xbin/daemonsu" "$MOUNT/xbin/sugote"

# sugote-mksh is a copy of the stock shell (SuperSU installer does the same).
sudo cp "$MOUNT/bin/sh" "$MOUNT/xbin/sugote-mksh"
sudo chmod 0755 "$MOUNT/xbin/sugote-mksh"

# API 19+ sepolicy helper + JNI shim for live policy patches on device.
sudo cp "$BIN/supolicy" "$MOUNT/xbin/supolicy"
sudo chmod 0755 "$MOUNT/xbin/supolicy"
sudo cp "$BIN/libsupol.so" "$MOUNT/lib/libsupol.so"
sudo chmod 0644 "$MOUNT/lib/libsupol.so"

sudo cp "$COM/Superuser.apk" "$MOUNT/app/Superuser.apk"
sudo chmod 0644 "$MOUNT/app/Superuser.apk"

sudo cp "$COM/install-recovery.sh" "$MOUNT/etc/install-recovery.sh"
sudo chmod 0755 "$MOUNT/etc/install-recovery.sh"
sudo ln -sf /system/etc/install-recovery.sh "$MOUNT/bin/install-recovery.sh"

# init.d hook launches daemonsu early on MTK KitKat (same as flashable zip).
sudo cp "$COM/99SuperSUDaemon" "$MOUNT/etc/init.d/99SuperSUDaemon"
sudo chmod 0755 "$MOUNT/etc/init.d/99SuperSUDaemon"

echo 1 | sudo tee "$MOUNT/etc/.installed_su_daemon" >/dev/null
sudo chmod 0644 "$MOUNT/etc/.installed_su_daemon"

sudo chown root:root \
    "$MOUNT/bin/.ext/.su" \
    "$MOUNT/xbin/su" "$MOUNT/xbin/daemonsu" "$MOUNT/xbin/sugote" "$MOUNT/xbin/sugote-mksh" \
    "$MOUNT/xbin/supolicy" "$MOUNT/lib/libsupol.so" \
    "$MOUNT/app/Superuser.apk" \
    "$MOUNT/etc/install-recovery.sh" "$MOUNT/etc/init.d/99SuperSUDaemon" \
    "$MOUNT/etc/.installed_su_daemon"

echo "==> SuperSU installed under /system (xbin/su, app/Superuser.apk)"
