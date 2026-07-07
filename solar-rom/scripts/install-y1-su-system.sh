#!/usr/bin/env bash
# Bake Y1-style permissive root into a loop-mounted /system image (KitKat armv7).
# Copies only setuid su/daemonsu — no Superuser.apk, daemons, or supolicy (no grant dialog).
set -euo pipefail

MOUNT="${1:?usage: install-y1-su-system.sh MOUNTED_SYSTEM_DIR}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-root.sh"
ensure_sudo_shim_when_root
SU="$SCRIPT_DIR/../vendor/y1-su/su"

die() {
    echo "error: $*" >&2
    exit 1
}

[ -d "$MOUNT/xbin" ] || die "not a system mount: $MOUNT"
[ -f "$SU" ] || die "missing $SU (run solar-rom/scripts/extract-y1-su-vendor.sh)"

echo "==> Install Y1 permissive su (system mode, armv7 KitKat)"

# Same layout as Y1 rockbox base: three copies of one setuid binary.
sudo mkdir -p "$MOUNT/bin/.ext" "$MOUNT/xbin" "$MOUNT/etc/init.d"
# install -m sets mode in one step (6755 = rwsr-sr-x, like stock Y1).
sudo install -m 6755 -o root -g root "$SU" "$MOUNT/bin/.ext/.su"
sudo install -m 6755 -o root -g root "$SU" "$MOUNT/xbin/su"
sudo install -m 6755 -o root -g root "$SU" "$MOUNT/xbin/daemonsu"
# Rockbox Connectivity.execShell and stock tools call plain "su" — ensure /system/bin is on PATH.
sudo install -m 6755 -o root -g root "$SU" "$MOUNT/bin/su"

# SuperSU on enforcing SELinux (Y2) needs daemonsu running before untrusted_app can su -c.
DAEMON_SCRIPT="$SCRIPT_DIR/../system/99SuperSUDaemon"
[ -f "$DAEMON_SCRIPT" ] || die "missing $DAEMON_SCRIPT"
sudo cp "$DAEMON_SCRIPT" "$MOUNT/etc/init.d/99SuperSUDaemon"
sudo chmod 755 "$MOUNT/etc/init.d/99SuperSUDaemon"
sudo chown root:root "$MOUNT/etc/init.d/99SuperSUDaemon"
echo 1 | sudo tee "$MOUNT/etc/.installed_su_daemon" >/dev/null
sudo chmod 644 "$MOUNT/etc/.installed_su_daemon"

# MTK init.rc: service flash_recovery /system/etc/install-recovery.sh — without this,
# daemonsu and /system/etc/init.d/* never run on Y2 (SELinux Enforcing, no app su -c).
RECOVERY="$SCRIPT_DIR/../system/install-recovery.sh"
RECOVERY2="$SCRIPT_DIR/../system/install-recovery-2.sh"
[ -f "$RECOVERY" ] || die "missing $RECOVERY"
[ -f "$RECOVERY2" ] || die "missing $RECOVERY2"
sudo cp "$RECOVERY" "$MOUNT/etc/install-recovery.sh"
sudo cp "$RECOVERY2" "$MOUNT/etc/install-recovery-2.sh"
sudo chmod 755 "$MOUNT/etc/install-recovery.sh" "$MOUNT/etc/install-recovery-2.sh"
sudo chown root:root "$MOUNT/etc/install-recovery.sh" "$MOUNT/etc/install-recovery-2.sh"
sudo ln -sf /system/etc/install-recovery.sh "$MOUNT/bin/install-recovery.sh" 2>/dev/null \
    || sudo cp "$RECOVERY" "$MOUNT/bin/install-recovery.sh"
sudo chmod 755 "$MOUNT/bin/install-recovery.sh" 2>/dev/null || true

# Re-assert setuid after any prior copies — Y2 ATA base can leave 0755 shells that break app su.
sudo chmod 6755 "$MOUNT/bin/.ext/.su" "$MOUNT/xbin/su" "$MOUNT/xbin/daemonsu" "$MOUNT/bin/su"

echo "==> Y1 permissive su installed (/system/xbin/su, install-recovery.sh, init.d hooks)"
