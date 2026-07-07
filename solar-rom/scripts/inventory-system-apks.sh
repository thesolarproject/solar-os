#!/usr/bin/env bash
# 2026-07-05 — List APK basenames under /system/app and /system/priv-app on a mounted tree or system.img.
# Reversal: delete this helper; strip/verify scripts can inline find(1) again.
# Usage: inventory-system-apks.sh MOUNTED_SYS_ROOT | system.img
set -euo pipefail

TARGET="${1:-}"
[ -n "$TARGET" ] || { echo "usage: $0 MOUNTED_SYS_ROOT|system.img" >&2; exit 1; }

inventory_mounted() {
    local root="$1"
    find "$root/app" "$root/priv-app" -maxdepth 1 -name '*.apk' -printf '%f\n' 2>/dev/null \
        | sort -u
}

inventory_image() {
    local img="$1"
    command -v debugfs >/dev/null 2>&1 || { echo "error: missing debugfs" >&2; exit 1; }
    {
        debugfs -R "ls -l /app" "$img" 2>/dev/null | awk '/\.apk$/ { print $NF }'
        debugfs -R "ls -l /priv-app" "$img" 2>/dev/null | awk '/\.apk$/ { print $NF }'
    } | sort -u
}

if [ -f "$TARGET" ] && [ "${TARGET##*/}" = "system.img" ] || file "$TARGET" 2>/dev/null | grep -qiE 'ext[24]|android sparse'; then
    inventory_image "$TARGET"
elif [ -d "$TARGET/app" ] || [ -d "$TARGET/priv-app" ]; then
    inventory_mounted "$TARGET"
else
    echo "error: $TARGET is not a mounted /system root or system.img" >&2
    exit 1
fi
