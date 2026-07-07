#!/usr/bin/env bash
# Extract armv7 + common SuperSU v2.76 files from a flashable zip into solar-rom/vendor/supersu/.
# Usage: extract-supersu-vendor.sh [/path/to/UPDATE-SuperSU-v2.76-*.zip]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENDOR="$SCRIPT_DIR/../vendor/supersu"
ZIP="${1:-}"

die() {
    echo "error: $*" >&2
    exit 1
}

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"
}

require_cmd unzip

if [ -z "$ZIP" ]; then
    for candidate in \
        "$HOME/Downloads/UPDATE-SuperSU-v2.76-"*.zip \
        "$SCRIPT_DIR/../vendor/UPDATE-SuperSU-v2.76.zip"; do
        if [ -f "$candidate" ]; then
            ZIP="$candidate"
            break
        fi
    done
fi

[ -n "$ZIP" ] && [ -f "$ZIP" ] || die "usage: $0 [/path/to/UPDATE-SuperSU-v2.76-*.zip]"

mkdir -p "$VENDOR/armv7" "$VENDOR/common"

echo "==> Extracting SuperSU v2.76 from $(basename "$ZIP")"
for path in armv7/su armv7/supolicy armv7/libsupol.so \
    common/Superuser.apk common/install-recovery.sh common/99SuperSUDaemon; do
    unzip -oq "$ZIP" "$path" -d "$VENDOR"
    case "$path" in
        armv7/*) dest="$VENDOR/$path" ;;
        common/*) dest="$VENDOR/$path" ;;
    esac
    [ -f "$dest" ] || die "zip missing $path"
done

cat > "$VENDOR/VENDORED.txt" <<EOF
SuperSU v2.76 (UPDATE-SuperSU-v2.76-20160630161323)
Copyright (c) 2012-2016 Chainfire — GPL-compatible distribution via flashable zip.
Vendored armv7 + common files only (MT6582 / Android 4.4 KitKat system install).
Source: $(basename "$ZIP")
Regenerate: solar-rom/scripts/extract-supersu-vendor.sh "$ZIP"
EOF

echo "==> Vendored under $VENDOR ($(du -sh "$VENDOR" | awk '{print $1}'))"
