#!/usr/bin/env bash
# 2026-07-05 — Remove /system APKs not on curated allowlist (APK + matching .odex).
# Reversal: skip this script in build-rom.sh; stock MTK packages return from base firmware.
# Usage: strip-nonessential-system-apps.sh MOUNTED_SYS_ROOT DEVICE_TYPE ALLOWLIST_FILE
set -euo pipefail

MOUNT_SYS="${1:-}"
DEVICE_TYPE="${2:-y1}"
ALLOWLIST="${3:-}"

die() { echo "strip-nonessential-system-apps: error: $*" >&2; exit 1; }

[ -d "$MOUNT_SYS/app" ] || die "missing $MOUNT_SYS/app"
[ -f "$ALLOWLIST" ] || die "missing allowlist: $ALLOWLIST"

# Load allowlist — skip comments and blank lines.
declare -A KEEP=()
while IFS= read -r line || [ -n "$line" ]; do
    line="${line%%#*}"
    line="$(echo "$line" | tr -d '[:space:]')"
    [ -n "$line" ] || continue
    KEEP["$line"]=1
done < "$ALLOWLIST"

removed=0
for dir in app priv-app; do
    [ -d "$MOUNT_SYS/$dir" ] || continue
    for apk in "$MOUNT_SYS/$dir"/*.apk; do
        [ -e "$apk" ] || continue
        base="$(basename "$apk")"
        if [ -n "${KEEP[$base]+x}" ]; then
            continue
        fi
        echo "  stripping /system/$dir/$base"
        rm -f "$apk" "${apk%.apk}.odex"
        removed=$((removed + 1))
    done
done

echo "==> strip-nonessential-system-apps ($DEVICE_TYPE): removed $removed APK(s)"
