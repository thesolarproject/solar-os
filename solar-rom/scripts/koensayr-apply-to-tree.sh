#!/usr/bin/env bash
# Apply Koensayr AVRCP patches + Y1Bridge.apk to a system tree (ROM mount or staging dir).
# Usage: koensayr-apply-to-tree.sh /path/to/system [--sudo]
set -euo pipefail

TARGET_SYS="${1:-}"
USE_SUDO=0
[[ "${2:-}" == "--sudo" ]] && USE_SUDO=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
KOENSAYR_DIR="${KOENSAYR_DIR:-$REPO_ROOT/solar-rom/koensayr}"
Y1_BRIDGE_APK="Y1Bridge.apk"
WORK_DIR=""

die() { echo "error: $*" >&2; exit 1; }

cleanup() {
    [[ -n "$WORK_DIR" && -d "$WORK_DIR" ]] && rm -rf "$WORK_DIR"
}
trap cleanup EXIT

[ -n "$TARGET_SYS" ] && [ -d "$TARGET_SYS" ] || die "usage: $0 /path/to/system [--sudo]"
[ -d "$KOENSAYR_DIR/src/patches" ] || die "Koensayr not found at $KOENSAYR_DIR"
command -v python3 >/dev/null 2>&1 || die "python3 required"

WORK_DIR="$(mktemp -d)"

do_cp() {
    if [[ "$USE_SUDO" -eq 1 ]]; then sudo cp "$@"; else cp "$@"; fi
}
do_install() {
    if [[ "$USE_SUDO" -eq 1 ]]; then
        sudo install "$@"
        return
    fi
    local mode=644
    local src="" dest=""
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -m) mode="$2"; shift 2 ;;
            -o|-g) shift 2 ;;
            *)
                if [[ -z "$src" ]]; then src="$1"; else dest="$1"; fi
                shift
                ;;
        esac
    done
    [[ -n "$src" && -n "$dest" && -f "$src" ]] || die "do_install: bad args"
    cp "$src" "$dest"
    chmod "$mode" "$dest"
}
do_chmod() {
    if [[ "$USE_SUDO" -eq 1 ]]; then sudo chmod "$@"; else chmod "$@"; fi
}
do_chown() {
    if [[ "$USE_SUDO" -eq 1 ]]; then sudo chown "$@"; else true; fi
}
do_mkdir() {
    if [[ "$USE_SUDO" -eq 1 ]]; then sudo mkdir -p "$@"; else mkdir -p "$@"; fi
}
do_sed() {
    if [[ "$USE_SUDO" -eq 1 ]]; then sudo sed -i "$@"; else sed -i "$@"; fi
}
do_tee_append() {
    if [[ "$USE_SUDO" -eq 1 ]]; then sudo tee -a "$1" >/dev/null; else tee -a "$1" >/dev/null; fi
}

read_system_file() {
    local rel="$1"
    local dest="$2"
    if [[ ! -f "$TARGET_SYS/$rel" ]]; then
        die "missing $TARGET_SYS/$rel"
    fi
    if [[ "$USE_SUDO" -eq 1 ]]; then
        sudo cp "$TARGET_SYS/$rel" "$dest"
        sudo chown "$(id -u):$(id -g)" "$dest"
    else
        cp "$TARGET_SYS/$rel" "$dest"
    fi
}

patch_into_tree() {
    local mount_rel="$1"
    local script="$2"
    local mode="${3:-644}"
    local stage="${WORK_DIR}/$(basename "$mount_rel")"
    local stock="${stage}/stock"
    local patched="${stage}/patched"
    mkdir -p "$stage"
    echo "  ${mount_rel}: ${script}"
    read_system_file "$mount_rel" "$stock"
    if ! python3 "$KOENSAYR_DIR/src/patches/$script" "$stock" --output "$patched" --skip-md5; then
        die "${script} failed for ${mount_rel}"
    fi
    if [[ ! -f "$patched" ]]; then
        cp "$stock" "$patched"
    fi
    [[ -f "$patched" ]] || die "no patched output for ${mount_rel}"
    do_mkdir "$(dirname "$TARGET_SYS/$mount_rel")"
    do_cp "$patched" "$TARGET_SYS/$mount_rel"
    do_chmod "$mode" "$TARGET_SYS/$mount_rel"
    do_chown root:root "$TARGET_SYS/$mount_rel"
}

build_y1_bridge() {
    local out="$1"
    local src="$KOENSAYR_DIR/src/Y1Bridge/app/build/outputs/apk/debug/app-debug.apk"
    if [[ ! -f "$src" ]]; then
        echo "==> Building Y1Bridge.apk"
        chmod +x "$KOENSAYR_DIR/src/Y1Bridge/gradlew" 2>/dev/null || true
        (cd "$KOENSAYR_DIR/src/Y1Bridge" && ./gradlew --stop >/dev/null 2>&1 || true)
        (cd "$KOENSAYR_DIR/src/Y1Bridge" && ./gradlew assembleDebug)
    fi
    [[ -f "$src" ]] || die "Y1Bridge build missing: $src"

    # shellcheck source=/dev/null
    [[ -f "$REPO_ROOT/scripts/env.sh" ]] && source "$REPO_ROOT/scripts/env.sh"
    local pk8="${SOLAR_PLATFORM_KEY_PK8:-}"
    local pem="${SOLAR_PLATFORM_KEY_PEM:-}"
    local apksigner="${ANDROID_HOME:-}/build-tools/35.0.0/apksigner"
    if [[ ! -x "$apksigner" && -d "${ANDROID_HOME:-}/build-tools" ]]; then
        apksigner="$(ls -1 "${ANDROID_HOME}/build-tools"/*/apksigner 2>/dev/null | tail -1)"
    fi
    if [[ -f "$pk8" && -f "$pem" && -n "$apksigner" && -x "$apksigner" ]]; then
        echo "==> Signing Y1Bridge with platform key"
        "$apksigner" sign --key "$pk8" --cert "$pem" --out "$out" "$src"
    else
        echo "==> Y1Bridge (debug-signed; set SOLAR_PLATFORM_KEY_* to platform-sign)"
        cp "$src" "$out"
    fi
}

apply_bt_config() {
    echo "==> Bluetooth profile build.prop + audio.conf"
    if [[ -f "$TARGET_SYS/etc/bluetooth/audio.conf" ]]; then
        do_sed 's/^Enable=.*/Enable=Source,Control,Target/' "$TARGET_SYS/etc/bluetooth/audio.conf" || true
        do_sed 's/^Master=.*/Master=true/' "$TARGET_SYS/etc/bluetooth/audio.conf" || true
    fi
    if [[ -f "$TARGET_SYS/etc/bluetooth/auto_pairing.conf" ]]; then
        do_sed 's/^AddressBlacklist=.*/AddressBlacklist=/' "$TARGET_SYS/etc/bluetooth/auto_pairing.conf" || true
        do_sed 's/^ExactNameBlacklist=.*/ExactNameBlacklist=/' "$TARGET_SYS/etc/bluetooth/auto_pairing.conf" || true
        do_sed 's/^PartialNameBlacklist=.*/PartialNameBlacklist=/' "$TARGET_SYS/etc/bluetooth/auto_pairing.conf" || true
    fi
    if [[ -f "$TARGET_SYS/etc/bluetooth/blacklist.conf" ]]; then
        do_sed '/^scoSocket/d' "$TARGET_SYS/etc/bluetooth/blacklist.conf" || true
    fi
    if [[ -f "$TARGET_SYS/build.prop" ]]; then
        if ! grep -q 'ro.bluetooth.profiles.avrcp.target.enabled=true' "$TARGET_SYS/build.prop" 2>/dev/null; then
            do_tee_append "$TARGET_SYS/build.prop" <<'EOF'

# Solar / Koensayr AVRCP target profile
ro.bluetooth.class=10486812
ro.bluetooth.profiles.a2dp.source.enabled=true
ro.bluetooth.profiles.avrcp.target.enabled=true
EOF
        fi
    fi
}

echo "==> Koensayr AVRCP (from $KOENSAYR_DIR) -> $TARGET_SYS"

BRIDGE_OUT="$WORK_DIR/Y1Bridge.apk"
build_y1_bridge "$BRIDGE_OUT"
do_mkdir "$TARGET_SYS/app"
do_install -m 644 -o root -g root "$BRIDGE_OUT" "$TARGET_SYS/app/$Y1_BRIDGE_APK"

patch_into_tree "app/MtkBt.odex" "patch_mtkbt_odex.py" 644
patch_into_tree "bin/mtkbt" "patch_mtkbt.py" 755
patch_into_tree "lib/libextavrcp_jni.so" "patch_libextavrcp_jni.py" 644
patch_into_tree "lib/libextavrcp.so" "patch_libextavrcp.py" 644
patch_into_tree "lib/libaudio.a2dp.default.so" "patch_libaudio_a2dp.py" 644
patch_into_tree "usr/keylayout/AVRCP.kl" "patch_avrcp_kl.py" 644

apply_bt_config
echo "==> Koensayr applied to $TARGET_SYS"
