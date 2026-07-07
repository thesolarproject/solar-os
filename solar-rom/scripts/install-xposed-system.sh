#!/usr/bin/env bash
# 2026-07-05 — Bakes Xposed Dalvik framework into loop-mounted /system (Y1 API 17, Y2 API 19).
# APK/ROM parity: same paths as sync-platform-assets.sh manifest systemPaths; APK prep stages gaps OTA.
# When changing: lib-xposed-install.sh constants; build-rom.sh must call this for a, b, and y2 zips.
# Reversal: skip call in build-rom.sh; ROM ships without Xposed framework again.
# Usage: install-xposed-system.sh MOUNTED_SYSTEM_DIR API_LEVEL
set -euo pipefail

MOUNT="${1:?usage: install-xposed-system.sh MOUNTED_SYSTEM_DIR API_LEVEL}"
API="${2:?usage: install-xposed-system.sh MOUNTED_SYSTEM_DIR API_LEVEL}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-xposed-install.sh"

# Snapshot stock app_process before we overwrite (ROM first install only).
if [ ! -f "$MOUNT/bin/app_process.orig" ] && [ -f "$MOUNT/bin/app_process" ]; then
    # shellcheck source=/dev/null
    source "$SCRIPT_DIR/lib-root.sh"
    ensure_sudo_shim_when_root
    sudo cp -a "$MOUNT/bin/app_process" "$MOUNT/bin/app_process.orig"
    sudo chmod 755 "$MOUNT/bin/app_process.orig"
    echo "==> Xposed: saved stock /system/bin/app_process.orig"
fi

xposed_install_to_mount "$MOUNT" "$API" "$SCRIPT_DIR"
echo "==> Xposed: framework installed for API $API"
