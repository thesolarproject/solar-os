#!/usr/bin/env bash
# Repair Xposed Installer /data/data ownership so the app can toggle modules in the UI.
# Root/su seeding leaves modules.list root-owned — Installer gets EACCES on write.
# Usage: fix-xposed-installer-data-adb.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-adb-system.sh"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-xposed-install.sh"

adb_system_init
adb_system_preflight

echo "==> fix-xposed-installer-data (before)"
xposed_probe_installer_writable_via_adb || true

echo "==> chown /data/data/de.robv.android.xposed.installer to Installer app uid"
xposed_fix_installer_data_ownership_via_adb

echo "==> fix-xposed-installer-data (after)"
xposed_probe_installer_writable_via_adb || true
echo "==> done — open Xposed Installer and toggle a module to confirm"
