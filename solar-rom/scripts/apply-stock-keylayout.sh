#!/usr/bin/env bash
# Install Innioasis stock keymaps for Solar sideload (wheel 21/22, prev/next 88/87).
# Usage: apply-stock-keylayout.sh <mounted_system>
set -euo pipefail

MOUNT_SYS="${1:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

die() { echo "error: $*" >&2; exit 1; }

[ -n "$MOUNT_SYS" ] && [ -d "$MOUNT_SYS" ] || die "usage: $0 /path/to/mounted/system"
[ -f "$SCRIPT_DIR/Stock.kl" ] || die "missing $SCRIPT_DIR/Stock.kl"
[ -f "$SCRIPT_DIR/mtk-kpd.kl" ] || die "missing $SCRIPT_DIR/mtk-kpd.kl"

KL_DIR="$MOUNT_SYS/usr/keylayout"
sudo mkdir -p "$KL_DIR"

echo "==> Stock keylayout (Generic.kl + Stock.kl + mtk-kpd.kl)"
sudo cp "$SCRIPT_DIR/Stock.kl" "$KL_DIR/Stock.kl"
sudo cp "$SCRIPT_DIR/Stock.kl" "$KL_DIR/Generic.kl"
sudo cp "$SCRIPT_DIR/mtk-kpd.kl" "$KL_DIR/mtk-kpd.kl"
sudo rm -f "$KL_DIR/Rockbox.kl"
sudo chmod 644 "$KL_DIR/Stock.kl" "$KL_DIR/Generic.kl" "$KL_DIR/mtk-kpd.kl"
sudo chown root:root "$KL_DIR/Stock.kl" "$KL_DIR/Generic.kl" "$KL_DIR/mtk-kpd.kl"
