#!/usr/bin/env bash
# Shared adb + su helpers for live /system patching (Y1/Y2 ROM overlay scripts).
# Source from apply-*-rom-patches-adb.sh, install-xposed-adb.sh, push-avrcp-patches.sh.
set -euo pipefail

# Call after SCRIPT_DIR / REPO_ROOT are set.
adb_system_init() {
    # 2026-07-06 — Keep SOLAR_ADB_SERIAL from env when ANDROID_SERIAL unset (multi-USB lab).
    SOLAR_ADB_SERIAL="${ANDROID_SERIAL:-${SOLAR_DEVICE_SERIAL:-${SOLAR_ADB_SERIAL:-}}}"
    SOLAR_ADB_TRANSPORT="${ANDROID_ADB_TRANSPORT:-}"
    SOLAR_ADB=(adb)
    if [ -n "$SOLAR_ADB_TRANSPORT" ]; then
        SOLAR_ADB=(adb -t "$SOLAR_ADB_TRANSPORT")
    elif [ -n "$SOLAR_ADB_SERIAL" ]; then
        SOLAR_ADB=(adb -s "$SOLAR_ADB_SERIAL")
    fi
}

adb_system_die() {
    echo "adb-system: $*" >&2
    exit 1
}

# Read ro.product.model from the currently selected adb target (-t / -s).
adb_system_read_model() {
    "${SOLAR_ADB[@]}" shell getprop ro.product.model 2>/dev/null | tr -d '\r\n'
}

# Abort when SOLAR_EXPECT_MODEL=y1|y2 does not match getprop (duplicate serial safety).
adb_system_assert_model() {
    local expect="$1"
    local model
    model="$(adb_system_read_model | tr '[:upper:]' '[:lower:]')"
    expect="$(printf '%s' "$expect" | tr '[:upper:]' '[:lower:]')"
    if [ -z "$model" ] || [ "${model#*$expect}" = "$model" ]; then
        adb_system_die "model mismatch: got '${model:-unknown}' expected *${expect}* (transport=${SOLAR_ADB_TRANSPORT:-default} serial=${SOLAR_ADB_SERIAL:-default})"
    fi
    echo "adb-system: model OK ($model transport=${SOLAR_ADB_TRANSPORT:-default})"
}

# Wait for device, verify uid=0 su, remount /system rw.
adb_system_preflight() {
    command -v adb >/dev/null || adb_system_die "adb not in PATH"
    "${SOLAR_ADB[@]}" wait-for-device
    if [ -n "${SOLAR_EXPECT_MODEL:-}" ]; then
        adb_system_assert_model "$SOLAR_EXPECT_MODEL"
    fi
    if ! "${SOLAR_ADB[@]}" shell "su -c id" 2>/dev/null | grep -q uid=0; then
        adb_system_die "device su unavailable — need root for /system writes"
    fi
    # ponytail: adb root restarts adbd and breaks transport IDs on duplicate-serial USB hubs; su -c handles all system writes.
    # "${SOLAR_ADB[@]}" root 2>/dev/null || true
    # sleep 1
    # "${SOLAR_ADB[@]}" remount 2>/dev/null || true
    "${SOLAR_ADB[@]}" shell "su -c 'mount -o remount,rw /system'" 2>/dev/null \
        || adb_system_die "could not remount /system rw"
}

# Push local file to absolute /system path via /data/local/tmp staging + su cp.
# Use cat redirect for /system/bin/app_process — cp fails with "Text file busy" on live zygote.
adb_push_to_system() {
    local local_file="$1" device_path="$2" mode="$3"
    local tmp="/data/local/tmp/solar-adb-$(basename "$device_path")"
    local parent="${device_path%/*}"
    [ -f "$local_file" ] || adb_system_die "missing local file: $local_file"
    "${SOLAR_ADB[@]}" push "$local_file" "$tmp" >/dev/null || adb_system_die "adb push $local_file"
    if [ "$device_path" = "/system/bin/app_process" ]; then
        # Live zygote holds app_process open — cat often hits ETXTBUSY; adb shell exit code stays 0 anyway.
        "${SOLAR_ADB[@]}" shell "su -c \"cat '$tmp' > '$device_path'\"" 2>/dev/null || true
        if adb_su_sh "grep -aq 'with Xposed support' '$device_path' && echo yes || echo no" \
                | tr -d '\r' | grep -q yes; then
            "${SOLAR_ADB[@]}" shell "su -c \"chmod $mode '$device_path' && chown root:shell '$device_path' && rm -f '$tmp'\"" \
                || adb_system_die "chmod/chown $device_path failed"
        else
            echo "==> app_process busy — staging for reboot (99XposedInit.sh will apply)"
            adb_su_sh "cp '$tmp' /system/bin/app_process.xposed.staged && chmod $mode /system/bin/app_process.xposed.staged && rm -f '$tmp'" \
                || adb_system_die "stage app_process.xposed.staged failed"
        fi
        return 0
    fi
    if [ "$parent" != "$device_path" ] && [ -n "$parent" ]; then
        "${SOLAR_ADB[@]}" shell "su -c \"mkdir -p '$parent'\"" || adb_system_die "mkdir $parent"
    fi
    "${SOLAR_ADB[@]}" shell "su -c \"cp '$tmp' '$device_path' && chmod $mode '$device_path' && rm -f '$tmp'\"" \
        || adb_system_die "su cp to $device_path failed"
}

# Copy a local directory tree into /system path (device has no tar).
adb_push_dir_to_system() {
    local local_dir="$1" device_path="$2"
    [ -d "$local_dir" ] || adb_system_die "missing local dir: $local_dir"
    "${SOLAR_ADB[@]}" shell "su -c 'mount -o remount,rw /system'" 2>/dev/null || true
    "${SOLAR_ADB[@]}" shell "su -c \"mkdir -p '$device_path' && rm -rf '$device_path'/*\"" 2>/dev/null || true
    "${SOLAR_ADB[@]}" push "$local_dir/." "/data/local/tmp/solar-adb-dir/" >/dev/null \
        || adb_system_die "adb push dir $local_dir"
    "${SOLAR_ADB[@]}" shell "su -c \"mkdir -p '$device_path' && cp -a /data/local/tmp/solar-adb-dir/. '$device_path/' && chmod -R 755 '$device_path' && rm -rf /data/local/tmp/solar-adb-dir\"" \
        || adb_system_die "cp tree to $device_path failed"
}

# Run adb shell as root — pass one shell command string (paths must not contain spaces).
adb_su_sh() {
    local cmd="$1"
    "${SOLAR_ADB[@]}" shell "su -c \"$cmd\"" 2>/dev/null
}

# 2026-07-06 — pm install from /system/app; optional pkg wipes /data/app drift first.
adb_pm_install_internal() {
    local apk_path="$1"
    local pkg="${2:-}"
    [ -n "$apk_path" ] || adb_system_die "adb_pm_install_internal: missing apk path"
    adb_su_sh "pm set-install-location 1"
    if [ -n "$pkg" ]; then
        adb_su_sh "rm -rf /data/app/${pkg}* /data/app-lib/${pkg}*" 2>/dev/null || true
    fi
    adb_su_sh "pm install -r -f '$apk_path'"
}
