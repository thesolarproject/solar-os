#!/usr/bin/env bash
# Install rockbox-y1 Rockbox.kl + mtk-kpd for Solar ROM.
# Usage: apply-rockbox-keylayout.sh <mounted_system>
set -euo pipefail

MOUNT_SYS="${1:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

die() { echo "error: $*" >&2; exit 1; }

[ -n "$MOUNT_SYS" ] && [ -d "$MOUNT_SYS" ] || die "usage: $0 /path/to/mounted/system"
[ -f "$SCRIPT_DIR/Rockbox.kl" ] || die "missing $SCRIPT_DIR/Rockbox.kl"
[ -f "$SCRIPT_DIR/mtk-kpd-rockbox.kl" ] || die "missing $SCRIPT_DIR/mtk-kpd-rockbox.kl"
[ -f "$SCRIPT_DIR/mtk_kpd.kl" ] || die "missing $SCRIPT_DIR/mtk_kpd.kl (Android 4.2 underscore name for mtk-kpd device)"

KL_DIR="$MOUNT_SYS/usr/keylayout"
sudo mkdir -p "$KL_DIR"

echo "==> Rockbox keylayout (Rockbox.kl + mtk_kpd.kl)"
sudo cp "$SCRIPT_DIR/Rockbox.kl" "$KL_DIR/Stock.kl"
sudo cp "$SCRIPT_DIR/Rockbox.kl" "$KL_DIR/Generic.kl"
sudo cp "$SCRIPT_DIR/Rockbox.kl" "$KL_DIR/Rockbox.kl"
sudo cp "$SCRIPT_DIR/mtk-kpd-rockbox.kl" "$KL_DIR/mtk-kpd.kl"
sudo cp "$SCRIPT_DIR/mtk_kpd.kl" "$KL_DIR/mtk_kpd.kl"
sudo chmod 644 "$KL_DIR/Stock.kl" "$KL_DIR/Generic.kl" "$KL_DIR/Rockbox.kl" "$KL_DIR/mtk-kpd.kl" "$KL_DIR/mtk_kpd.kl"
sudo chown root:root "$KL_DIR/Stock.kl" "$KL_DIR/Generic.kl" "$KL_DIR/Rockbox.kl" "$KL_DIR/mtk-kpd.kl" "$KL_DIR/mtk_kpd.kl"
