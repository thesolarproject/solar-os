#!/usr/bin/env bash
# Bake Y1-style permissive root into a loop-mounted /system image (KitKat armv7).
# Copies only setuid su/daemonsu — no Superuser.apk, daemons, or supolicy (no grant dialog).
set -euo pipefail

MOUNT="${1:?usage: install-y1-su-system.sh MOUNTED_SYSTEM_DIR}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SU="$SCRIPT_DIR/../vendor/y1-su/su"

die() {
    echo "error: $*" >&2
    exit 1
}

[ -d "$MOUNT/xbin" ] || die "not a system mount: $MOUNT"
[ -f "$SU" ] || die "missing $SU (run solar-rom/scripts/extract-y1-su-vendor.sh)"

echo "==> Install Y1 permissive su (system mode, armv7 KitKat)"

# Same layout as Y1 rockbox base: three copies of one setuid binary.
sudo mkdir -p "$MOUNT/bin/.ext" "$MOUNT/xbin"
# install -m sets mode in one step (6755 = rwsr-sr-x, like stock Y1).
sudo install -m 6755 -o root -g root "$SU" "$MOUNT/bin/.ext/.su"
sudo install -m 6755 -o root -g root "$SU" "$MOUNT/xbin/su"
sudo install -m 6755 -o root -g root "$SU" "$MOUNT/xbin/daemonsu"

echo "==> Y1 permissive su installed (/system/xbin/su, no Superuser.apk)"
