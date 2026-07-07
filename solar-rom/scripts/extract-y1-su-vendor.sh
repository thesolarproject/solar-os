#!/usr/bin/env bash
# Extract permissive /system/xbin/su from the Y1 rockbox type-A base into solar-rom/vendor/y1-su/.
# Y1 ships setuid su + daemonsu only (no Superuser.apk) — grants root without a permission dialog.
# Usage: extract-y1-su-vendor.sh [/path/to/y1-type-a-rom.zip]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENDOR="$SCRIPT_DIR/../vendor/y1-su"
ZIP="${1:-}"
BASE_URL="https://github.com/rockbox-y1/rockbox/releases/download/type-a-base/rom.zip"

die() {
    echo "error: $*" >&2
    exit 1
}

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"
}

require_cmd curl
require_cmd unzip
require_cmd sudo

WORK="$(mktemp -d "${TMPDIR:-/tmp}/y1-su-vendor-XXXXXX")"
cleanup() {
    if mountpoint -q "$WORK/mount" 2>/dev/null; then
        sudo umount "$WORK/mount" 2>/dev/null || true
    fi
    rm -rf "$WORK"
}
trap cleanup EXIT

if [ -z "$ZIP" ]; then
    echo "==> Downloading Y1 type-A base"
    curl -fsSL -o "$WORK/rom.zip" "$BASE_URL"
    ZIP="$WORK/rom.zip"
fi

[ -f "$ZIP" ] || die "usage: $0 [/path/to/y1-type-a-rom.zip]"

echo "==> Unpacking $(basename "$ZIP")"
unzip -q "$ZIP" system.img -d "$WORK" || unzip -q "$ZIP" '*/system.img' -d "$WORK"
SYSIMG="$(find "$WORK" -name system.img | head -1)"
[ -f "$SYSIMG" ] || die "zip missing system.img"

mkdir -p "$WORK/mount" "$VENDOR"
echo "==> Mounting system.img read-only"
sudo mount -o loop,ro "$SYSIMG" "$WORK/mount"
[ -f "$WORK/mount/xbin/su" ] || die "Y1 base missing /system/xbin/su"

mkdir -p "$VENDOR"
cp "$WORK/mount/xbin/su" "$VENDOR/su"
chmod 644 "$VENDOR/su"

cat > "$VENDOR/VENDORED.txt" <<EOF
Y1 rockbox type-A permissive su (setuid /system/xbin/su, no Superuser.apk)
Source: $(basename "$ZIP") — rockbox-y1 type-a-base rom.zip
Regenerate: solar-rom/scripts/extract-y1-su-vendor.sh "$ZIP"
EOF

echo "==> Vendored under $VENDOR ($(du -sh "$VENDOR" | awk '{print $1}'))"
