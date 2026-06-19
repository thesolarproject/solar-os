#!/usr/bin/env bash
# Apply Koensayr AVRCP 1.3 + Bluetooth profile patches to a mounted system image.
# Usage: apply-koensayr-avrcp.sh /path/to/mounted/system
set -euo pipefail

MOUNT_SYS="${1:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

die() { echo "error: $*" >&2; exit 1; }

[ -n "$MOUNT_SYS" ] && [ -d "$MOUNT_SYS" ] || die "usage: $0 /path/to/mounted/system"

exec "$SCRIPT_DIR/koensayr-apply-to-tree.sh" "$MOUNT_SYS" --sudo
