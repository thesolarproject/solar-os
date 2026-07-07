#!/usr/bin/env bash
# 2026-07-06 — Y2 boot hang probe via adb (flashing-tool recovery triage).
set -euo pipefail

SERIAL="${1:-}"
DEBUG_LOG="${DEBUG_LOG:-/home/deck/Documents/Cursor Workspaces/TheSolarProject/solar/.cursor/debug-5ad34e.log}"
ADB=(adb)
[ -n "$SERIAL" ] && ADB=(adb -s "$SERIAL")

log_json() {
    local hypothesis_id="$1" message="$2" data="$3"
    local ts
    ts="$(date +%s000 2>/dev/null || echo 0)"
    printf '{"sessionId":"5ad34e","hypothesisId":"%s","location":"diagnose-y2-boot-adb.sh","message":"%s","data":%s,"timestamp":%s}\n' \
        "$hypothesis_id" "$message" "$data" "$ts" >> "$DEBUG_LOG" 2>/dev/null || true
}

die() { echo "error: $*" >&2; exit 1; }

if [ -z "$SERIAL" ]; then
    mapfile -t devs < <("${ADB[@]}" devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1}')
    for d in "${devs[@]}"; do
        model=$("${ADB[@]}" -s "$d" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || true)
        if [ -z "$model" ] || [[ "$model" == *"failed"* ]]; then
            SERIAL="$d"
            break
        fi
    done
    [ -n "$SERIAL" ] || die "no Y2-like adb device (pass serial or connect Y2 only)"
fi
ADB=(adb -s "$SERIAL")

echo "==> Diagnose Y2 boot (serial $SERIAL)"
log_json "H0" "diagnose start" "{\"serial\":\"$SERIAL\"}"

shell_ok="no"
if "${ADB[@]}" shell id 2>/dev/null | grep -q 'uid=0\|uid=2000'; then
    shell_ok="yes"
fi
log_json "H1" "adb shell" "{\"shell_ok\":\"$shell_ok\"}"

model=$("${ADB[@]}" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo unknown)
boot_completed=$("${ADB[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || echo unknown)
bootanim=$("${ADB[@]}" shell getprop init.svc.bootanim 2>/dev/null | tr -d '\r' || echo unknown)
zygote=$("${ADB[@]}" shell getprop init.svc.zygote 2>/dev/null | tr -d '\r' || echo unknown)
log_json "H2" "boot props" "{\"model\":\"$model\",\"boot_completed\":\"$boot_completed\",\"bootanim\":\"$bootanim\",\"zygote\":\"$zygote\"}"

if [ "$shell_ok" = "yes" ]; then
    mounts=$("${ADB[@]}" shell cat /proc/mounts 2>/dev/null | tr -d '\r' | grep -E ' /system | /data ' || true)
    log_json "H3" "mounts" "{\"lines\":\"$(echo "$mounts" | tr '\n' ';')\"}"
    echo "$mounts"
    "${ADB[@]}" logcat -d -t 80 2>/dev/null | grep -iE 'vold|fsck|ext4|mount|Fatal|AndroidRuntime|Xposed' | tail -30 || true
else
    echo "adb shell unavailable (/system/bin/sh missing — /system or /data likely not mounted)"
    log_json "H3" "mounts" "{\"lines\":\"shell_failed\"}"
    echo "Recovery: ./solar-rom/scripts/recover-y2-mtk-flash.sh dist/rom_y2.zip"
    echo "Then: RUN_MTK=1 ./solar-rom/scripts/recover-y2-mtk-flash.sh dist/rom_y2.zip"
fi

echo "==> Log: $DEBUG_LOG"
