#!/usr/bin/env bash
# Remove Xposed from a live device — restore stock app_process (boot-loop recovery).
# Usage: uninstall-xposed-adb.sh [--no-reboot]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-adb-system.sh"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib-xposed-install.sh"

REBOOT=1
[ "${1:-}" = "--no-reboot" ] && REBOOT=0

adb_system_init
adb_system_preflight

echo "==> Uninstall Xposed framework"

if adb_su_sh "[ -f '$XPOSED_SYSTEM_BIN_APP_PROCESS_ORIG' ] && echo yes || echo no" | tr -d '\r' | grep -q yes; then
    adb_su_sh "cp -a $XPOSED_SYSTEM_BIN_APP_PROCESS_ORIG $XPOSED_SYSTEM_BIN_APP_PROCESS && chmod 755 $XPOSED_SYSTEM_BIN_APP_PROCESS && chown root:shell $XPOSED_SYSTEM_BIN_APP_PROCESS" \
        || adb_system_die "restore app_process.orig failed"
    echo "  restored $XPOSED_SYSTEM_BIN_APP_PROCESS from .orig"
else
    echo "  WARN: $XPOSED_SYSTEM_BIN_APP_PROCESS_ORIG missing — cannot restore stock zygote" >&2
fi

while IFS= read -r path; do
    [ "$path" = "$XPOSED_SYSTEM_BIN_APP_PROCESS" ] && continue
    [ "$path" = "$XPOSED_SYSTEM_BIN_APP_PROCESS_ORIG" ] && continue
    adb_su_sh "rm -f '$path'" 2>/dev/null || true
    echo "  removed $path"
done < <(xposed_list_system_paths)

adb_su_sh "rm -rf /data/data/de.robv.android.xposed.installer" 2>/dev/null || true
adb_su_sh "sync"

if [ "$REBOOT" -eq 1 ]; then
    echo "==> rebooting"
    "${SOLAR_ADB[@]}" reboot
fi

echo "==> Xposed uninstall complete"
