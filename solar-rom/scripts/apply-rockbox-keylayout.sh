#!/usr/bin/env bash
# Install Rockbox-Y1 mtk-kpd for Solar ROM (wheel 19/20, prev/next 21/22).
# Usage: apply-rockbox-keylayout.sh <mounted_system>
set -euo pipefail

MOUNT_SYS="${1:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

die() { echo "error: $*" >&2; exit 1; }

[ -n "$MOUNT_SYS" ] && [ -d "$MOUNT_SYS" ] || die "usage: $0 /path/to/mounted/system"
[ -f "$SCRIPT_DIR/Generic-rockbox.kl" ] || die "missing $SCRIPT_DIR/Generic-rockbox.kl"
[ -f "$SCRIPT_DIR/mtk-kpd-rockbox.kl" ] || die "missing $SCRIPT_DIR/mtk-kpd-rockbox.kl"

KL_DIR="$MOUNT_SYS/usr/keylayout"
sudo mkdir -p "$KL_DIR"

echo "==> Rockbox keylayout (Generic-rockbox.kl + mtk-kpd-rockbox.kl)"
sudo cp "$SCRIPT_DIR/Generic-rockbox.kl" "$KL_DIR/Stock.kl"
sudo cp "$SCRIPT_DIR/Generic-rockbox.kl" "$KL_DIR/Generic.kl"
sudo cp "$SCRIPT_DIR/mtk-kpd-rockbox.kl" "$KL_DIR/mtk-kpd.kl"
sudo rm -f "$KL_DIR/Rockbox.kl"
sudo chmod 644 "$KL_DIR/Stock.kl" "$KL_DIR/Generic.kl" "$KL_DIR/mtk-kpd.kl"
sudo chown root:root "$KL_DIR/Stock.kl" "$KL_DIR/Generic.kl" "$KL_DIR/mtk-kpd.kl"
