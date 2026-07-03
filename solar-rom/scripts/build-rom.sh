#!/usr/bin/env bash
# Build a Solar launcher ROM from Y1 type-A/B or Y2 ATA base firmware.
# Usage: build-rom.sh <a|b|y2> --apk PATH [output.zip]
set -euo pipefail

TYPE=""
SOLAR_APK=""
SOLAR_TAG=""
SOLAR_APK_URL=""
OUTPUT=""
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
WORK_DIR=""
MOUNT_SYS=""
MOUNT_USER=""
SYSTEM_APK_NAME="com.solar.launcher.apk"

# shellcheck source=/dev/null
source "$SCRIPT_DIR/solar-repo.sh"

usage() {
    cat >&2 <<EOF
usage: $0 <a|b|y2> (--apk PATH | [--solar-tag TAG] [--solar-apk-url URL]) [output.zip]

  a|b|y2              Y1 type A (2.0.0+), Y1 type B (pre-2.0.0), or Y2 ATA (MT6582)
  --apk PATH          Local signed app-release.apk (CI / local builds)
  --solar-tag         GitHub release tag on ${SOLAR_GITHUB_REPO} (default: latest)
  --solar-apk-url     Direct APK download URL (skips GitHub HTML lookup)
  output.zip          Output archive path
EOF
    exit 1
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        a|b|y2)
            TYPE="$1"
            shift
            ;;
        --apk)
            SOLAR_APK="${2:-}"
            [ -n "$SOLAR_APK" ] || usage
            shift 2
            ;;
        --solar-tag)
            SOLAR_TAG="${2:-}"
            [ -n "$SOLAR_TAG" ] || usage
            shift 2
            ;;
        --solar-apk-url)
            SOLAR_APK_URL="${2:-}"
            [ -n "$SOLAR_APK_URL" ] || usage
            shift 2
            ;;
        -h|--help)
            usage
            ;;
        *)
            if [ -z "$OUTPUT" ]; then
                OUTPUT="$1"
                shift
            else
                usage
            fi
            ;;
    esac
done

[ -n "$TYPE" ] || usage

cleanup() {
    if [ -n "$MOUNT_SYS" ] && mountpoint -q "$MOUNT_SYS" 2>/dev/null; then
        sudo umount "$MOUNT_SYS" || true
    fi
    if [ -n "$MOUNT_USER" ] && mountpoint -q "$MOUNT_USER" 2>/dev/null; then
        sudo umount "$MOUNT_USER" || true
    fi
    if [ -n "$WORK_DIR" ] && [ -d "$WORK_DIR" ]; then
        rm -rf "$WORK_DIR"
    fi
}
trap cleanup EXIT

die() {
    echo "error: $*" >&2
    exit 1
}

# Y1 rockbox bases unpack flat; Y2 ATA ships images under a versioned subfolder.
normalize_firmware_layout() {
    if [ -f "$BASE_DIR/system.img" ]; then
        return 0
    fi
    local fw_sub
    fw_sub=$(find "$BASE_DIR" -mindepth 1 -maxdepth 3 -name system.img -printf '%h\n' 2>/dev/null | head -1)
    if [ -z "$fw_sub" ] || [ "$fw_sub" = "$BASE_DIR" ]; then
        die "base firmware missing system.img (expected flat zip or single subfolder)"
    fi
    echo "==> Normalizing nested base firmware layout ($(basename "$fw_sub") -> base)"
    shopt -s dotglob nullglob
    mv "$fw_sub"/* "$BASE_DIR/"
    shopt -u dotglob nullglob
    rmdir "$fw_sub" 2>/dev/null || true
}

# Y2 ships Android sparse images; loop mount needs raw ext4 (Y1 bases are already raw).
is_sparse_android_image() {
    local img="$1"
    local magic
    magic=$(dd if="$img" bs=1 count=4 2>/dev/null | od -An -tx1 | tr -d ' \n')
    [ "$magic" = "3aff26ed" ]
}

prepare_image_for_mount() {
    local img="$1"
    if is_sparse_android_image "$img"; then
        require_cmd simg2img
        local raw="${img%.img}.mount.raw"
        echo "==> Converting sparse $(basename "$img") to raw for loop mount" >&2
        simg2img "$img" "$raw"
        printf '%s\n' "$raw"
    else
        printf '%s\n' "$img"
    fi
}

# MTK SP Flash Tool rejects corrupt ext4 sparse images — fsck raw before img2simg repack.
verify_sparse_roundtrip() {
    local sparse="$1"
    is_sparse_android_image "$sparse" || return 0
    require_cmd simg2img
    local verify="${sparse}.verify.raw"
    simg2img "$sparse" "$verify"
    tune2fs -l "$verify" >/dev/null 2>&1 \
        || die "sparse round-trip failed for $(basename "$sparse")"
    rm -f "$verify"
}

finalize_image_raw() {
    local shipped_path="$1"
    local mount_src="$2"
    if [ "$mount_src" = "$shipped_path" ]; then
        return 0
    fi
    require_cmd e2fsck
    sync
    set +e
    e2fsck -f -y "$mount_src" >/dev/null 2>&1
    set -e
    echo "==> Ship desparsed ext4 $(basename "$shipped_path") ($(du -h "$mount_src" | awk '{print $1}'))"
    rm -f "$shipped_path"
    mv -f "$mount_src" "$shipped_path"
    if is_sparse_android_image "$shipped_path"; then
        die "$(basename "$shipped_path") is still sparse after desparse"
    fi
}

finalize_image_after_mount() {
    local shipped_path="$1"
    local mount_src="$2"
    if [ "$mount_src" != "$shipped_path" ]; then
        require_cmd img2simg
        require_cmd e2fsck
        sync
        echo "==> Fsck $(basename "$shipped_path") before sparse repack"
        set +e
        e2fsck -f -y "$mount_src" >/dev/null 2>&1
        local fsck_rc=$?
        set -e
        # e2fsck returns 1 when it auto-fixed bitmap padding — that is OK for MTK sparse repack.
        if [ "$fsck_rc" -gt 1 ]; then
            die "e2fsck failed on $(basename "$mount_src") (exit $fsck_rc)"
        fi
        echo "==> Repacking $(basename "$shipped_path") to Android sparse"
        local tmp="${shipped_path}.repack.$$"
        # Y2 MTK DA is picky about sparse layout — -s matches vendor chunk style more closely.
        if [ "$TYPE" = "y2" ] && [ "$(basename "$shipped_path")" = "system.img" ]; then
            require_cmd resize2fs
            resize2fs -M "$mount_src" >/dev/null 2>&1 || true
            img2simg -s "$mount_src" "$tmp" 4096
        else
            img2simg "$mount_src" "$tmp" 4096
        fi
        mv -f "$tmp" "$shipped_path"
        rm -f "$mount_src"
        verify_sparse_roundtrip "$shipped_path"
    fi
}

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"
}

# Y2 ATA stock base has no Rockbox; fetch org.rockbox.apk + librockbox.so from rockbox-y1 type-A base.
install_rockbox_from_y1_base() {
    local mount_sys="$1"
    if [ -f "$mount_sys/app/org.rockbox.apk" ] && [ -f "$mount_sys/lib/librockbox.so" ]; then
        return 0
    fi
    chmod +x "$SCRIPT_DIR/fetch-rockbox-y1-y2-assets.sh"
    local cache
    cache="$("$SCRIPT_DIR/fetch-rockbox-y1-y2-assets.sh")"
    [ -f "$cache/org.rockbox.apk" ] && [ -f "$cache/librockbox.so" ] \
        || die "fetch-rockbox-y1-y2-assets.sh did not populate org.rockbox.apk + librockbox.so"
    echo "==> Y2 base lacks Rockbox — installing org.rockbox.apk + librockbox.so from rockbox-y1 type-A base"
    sudo cp "$cache/org.rockbox.apk" "$mount_sys/app/org.rockbox.apk"
    sudo cp "$cache/librockbox.so" "$mount_sys/lib/librockbox.so"
    sudo chmod 644 "$mount_sys/app/org.rockbox.apk" "$mount_sys/lib/librockbox.so"
    sudo chown root:root "$mount_sys/app/org.rockbox.apk" "$mount_sys/lib/librockbox.so"
}

case "$TYPE" in
    a)
        BASE_URL="https://github.com/rockbox-y1/rockbox/releases/download/type-a-base/rom.zip"
        OUTPUT="${OUTPUT:-$REPO_ROOT/rom.zip}"
        SCATTER_FILE="MT6572_Android_scatter.txt"
        ;;
    b)
        BASE_URL="https://github.com/rockbox-y1/rockbox/releases/download/type-b-base/rom.zip"
        OUTPUT="${OUTPUT:-$REPO_ROOT/rom_type_b.zip}"
        SCATTER_FILE="MT6572_Android_scatter.txt"
        ;;
    y2)
        # Y2 ATA (MT6582); Y1 permissive su baked below. CI publishes rom_y2.zip on main and nightly.
        BASE_URL="https://github.com/y1-community/y2-ata-rom/releases/download/y2-ata/rom.zip"
        OUTPUT="${OUTPUT:-$REPO_ROOT/rom_y2.zip}"
        SCATTER_FILE="MT6582_Android_scatter.txt"
        # Fail fast before downloading Y2 base if vendored Y1 su is missing.
        [ -f "$REPO_ROOT/solar-rom/vendor/y1-su/su" ] \
            || die "missing solar-rom/vendor/y1-su/su — run solar-rom/scripts/extract-y1-su-vendor.sh"
        ;;
    *)
        die "unknown type: $TYPE"
        ;;
esac

# Resolve OUTPUT to absolute path before cd into WORK_DIR (relative paths would land in tmpdir).
OUTPUT="$(realpath -m "$OUTPUT" 2>/dev/null || echo "$(pwd)/$(basename "$OUTPUT")")"

require_cmd curl
require_cmd unzip
require_cmd zip
require_cmd cmp
require_cmd openssl
require_cmd sudo

resolve_latest_solar_tag() {
    local release_url tag
    release_url="$(
        curl -fsSIL -A 'solar-rom-build/1.0' \
            "${SOLAR_GITHUB_URL}/releases/latest" \
        | awk -F': ' 'tolower($1) ~ /^location$/ { print $2 }' \
        | tr -d '\r' \
        | tail -1
    )"
    [ -n "$release_url" ] || die "could not resolve latest Solar release URL"
    tag="${release_url##*/}"
    [ -n "$tag" ] || die "could not parse latest Solar release tag"
    printf '%s' "$tag"
}

download_solar_apk() {
    local dest="$1"
    local tag="${2:-}"
    local apk_url="${3:-}"

    if [ -n "$apk_url" ]; then
        echo "Downloading $(basename "$apk_url")"
        curl -fsSL -o "$dest" "$apk_url"
        return
    fi

    if [ -z "$tag" ]; then
        tag="$(resolve_latest_solar_tag)"
    fi

    apk_url="$(
        curl -fsSL -A 'solar-rom-build/1.0' \
            "${SOLAR_GITHUB_URL}/releases/expanded_assets/${tag}" \
        | grep -Eo "href=\"/${SOLAR_GITHUB_REPO}/releases/download/[^\"]+app-release[^\"]*\\.apk\"" \
        | head -1 \
        | sed 's/^href="//; s/"$//'
    )"
    [ -n "$apk_url" ] || die "could not find app-release APK for release ${tag}"

    apk_url="https://github.com${apk_url}"
    echo "Downloading $(basename "$apk_url") from release ${tag}"
    curl -fsSL -o "$dest" "$apk_url"
}

SOLAR_SYS="$REPO_ROOT/solar-rom/system"

install_solar_boot_assets() {
    local base_dir="$1"
    local sys_mount="$2"

    if [ -f "$SOLAR_SYS/media/bootanimation.zip" ]; then
        echo "==> Install Solar boot animation (system/media/bootanimation.zip)"
        sudo mkdir -p "$sys_mount/media"
        sudo cp "$SOLAR_SYS/media/bootanimation.zip" "$sys_mount/media/bootanimation.zip"
        sudo chmod 644 "$sys_mount/media/bootanimation.zip"
        sudo chown root:root "$sys_mount/media/bootanimation.zip"
    else
        die "missing $SOLAR_SYS/media/bootanimation.zip"
    fi

    if [ -f "$SOLAR_SYS/bin/bootanimation" ]; then
        echo "==> Install Solar bootanimation binary (system/bin/bootanimation)"
        sudo mkdir -p "$sys_mount/bin"
        sudo cp "$SOLAR_SYS/bin/bootanimation" "$sys_mount/bin/bootanimation"
        sudo chmod 755 "$sys_mount/bin/bootanimation"
        sudo chown root:root "$sys_mount/bin/bootanimation"
    else
        die "missing $SOLAR_SYS/bin/bootanimation"
    fi

    if [ -f "$SOLAR_SYS/boot.img" ]; then
        # Y2 ATA ships MT6582 boot.img; solar-rom/system/boot.img is Y1/MT6572 — keep stock kernel.
        if [ "$TYPE" = "y2" ]; then
            echo "==> Keeping Y2 stock boot.img (MT6582 kernel; Solar boot animation is on /system)"
        else
            echo "==> Replace boot.img in ROM archive"
            cp "$SOLAR_SYS/boot.img" "$base_dir/boot.img"
        fi
    else
        die "missing $SOLAR_SYS/boot.img"
    fi

    if [ -f "$SOLAR_SYS/logo.bin" ]; then
        echo "==> Replace logo.bin in ROM archive"
        cp "$SOLAR_SYS/logo.bin" "$base_dir/logo.bin"
    else
        die "missing $SOLAR_SYS/logo.bin"
    fi
}

audit_rom_contents() {
    local base_dir="$1"
    local sys_mount="$2"
    local user_mount="$3"
    local errors=0

    echo "==> Auditing ROM contents (type ${TYPE}, scatter ${SCATTER_FILE})"

    for required in boot.img lk.bin logo.bin recovery.img system.img userdata.img "$SCATTER_FILE"; do
        if [ ! -f "$base_dir/$required" ]; then
            echo "audit fail: missing $required in ROM archive" >&2
            errors=$((errors + 1))
        fi
    done

    if find "$sys_mount/app" "$sys_mount/priv-app" -iname '*innioasis*' 2>/dev/null | grep -q .; then
        echo "audit fail: stock launcher APK still present under /system/app" >&2
        errors=$((errors + 1))
    fi

    if [ "$TYPE" = "y2" ] && find "$sys_mount/priv-app" -iname '*factorylauncher*' 2>/dev/null | grep -q .; then
        echo "audit fail: stock Y2 factory launcher still present in /system/priv-app" >&2
        errors=$((errors + 1))
    fi

    if [ "$TYPE" = "y2" ]; then
        boot_bytes=$(stat -c%s "$base_dir/boot.img" 2>/dev/null || echo 0)
        # ponytail: Y1 boot.img is ~4.7 MiB; Y2 MT6582 stock is ~5.5 MiB — wrong kernel bricks SP Flash writes.
        if [ "$boot_bytes" -lt 5700000 ] || [ "$boot_bytes" -gt 5900000 ]; then
            echo "audit fail: Y2 boot.img is ${boot_bytes} bytes (expected MT6582 stock ~5773312)" >&2
            errors=$((errors + 1))
        fi
    fi

    # Y2 gets Y1 permissive su baked in build-rom.sh; Y1 rockbox base already ships /system/xbin/su.
    if [ "$TYPE" = "y2" ]; then
        if [ ! -x "$sys_mount/xbin/su" ]; then
            echo "audit fail: /system/xbin/su missing (Y2 permissive root)" >&2
            errors=$((errors + 1))
        fi
        if [ ! -x "$sys_mount/xbin/daemonsu" ]; then
            echo "audit fail: /system/xbin/daemonsu missing (Y2 permissive root)" >&2
            errors=$((errors + 1))
        fi
        if [ ! -x "$sys_mount/bin/.ext/.su" ]; then
            echo "audit fail: /system/bin/.ext/.su missing (Y2 permissive root)" >&2
            errors=$((errors + 1))
        fi
        if [ ! -u "$sys_mount/xbin/su" ]; then
            echo "audit fail: /system/xbin/su not setuid (Y2 permissive root)" >&2
            errors=$((errors + 1))
        fi
        # Superuser.apk would re-enable grant prompts — Y1-style root omits it on purpose.
        if [ -f "$sys_mount/app/Superuser.apk" ]; then
            echo "audit fail: /system/app/Superuser.apk present (Y2 should use permissive su only)" >&2
            errors=$((errors + 1))
        fi
    fi

    if [ ! -f "$sys_mount/app/$SYSTEM_APK_NAME" ]; then
        echo "audit fail: $SYSTEM_APK_NAME missing from /system/app" >&2
        errors=$((errors + 1))
    fi

    if [ ! -f "$sys_mount/lib/libconscrypt_jni.so" ]; then
        echo "audit fail: libconscrypt_jni.so missing from /system/lib (OkHttp/Reach TLS)" >&2
        errors=$((errors + 1))
    fi

    if [ ! -f "$sys_mount/etc/security/cacerts/6187b673.0" ]; then
        echo "audit fail: ISRG Root X1 cacert missing (MediaPlayer/podcast HTTPS)" >&2
        errors=$((errors + 1))
    fi

    if [ ! -f "$sys_mount/etc/init.d/99SolarInit.sh" ]; then
        echo "audit fail: 99SolarInit.sh missing (SD Music/Podcasts/Themes + TLS sanity)" >&2
        errors=$((errors + 1))
    elif ! grep -q 'disable-rockbox-for-solar.sh' "$sys_mount/etc/init.d/99SolarInit.sh" 2>/dev/null; then
        echo "audit fail: 99SolarInit must run disable-rockbox-for-solar.sh on first boot" >&2
        errors=$((errors + 1))
    fi

    if [ -f "$sys_mount/etc/init.d/99Y1LauncherInit.sh" ]; then
        echo "audit fail: legacy 99Y1LauncherInit.sh still present" >&2
        errors=$((errors + 1))
    fi

    if [ ! -f "$sys_mount/app/org.rockbox.apk" ]; then
        echo "audit fail: org.rockbox.apk missing (launcher switch requires Rockbox)" >&2
        errors=$((errors + 1))
    fi

    if [ ! -f "$sys_mount/lib/librockbox.so" ]; then
        echo "audit fail: librockbox.so missing" >&2
        errors=$((errors + 1))
    fi

    # FM radio — warn when mtk FM stack is absent (Solar FM browse still opens; tune may fail).
    if ! find "$sys_mount/lib" -maxdepth 1 -name 'libfm*.so' 2>/dev/null | grep -q . \
            && ! find "$sys_mount" \( -iname '*mediatek*fm*' -o -iname '*FMRadio*' \) 2>/dev/null | grep -q .; then
        echo "audit warn: no libfm* or MediaTek FM package found on /system (hardware FM may be unavailable)" >&2
    fi

    # ponytail: codec plugins ship inside org.rockbox.apk (lib/armeabi/*.so) — must survive ROM build.
    if [ -f "$sys_mount/app/org.rockbox.apk" ]; then
        rb_so_count=$(unzip -l "$sys_mount/app/org.rockbox.apk" 2>/dev/null \
            | grep -c 'lib/armeabi/.*\.so' || true)
        if [ "${rb_so_count:-0}" -lt 35 ]; then
            echo "audit fail: org.rockbox.apk has ${rb_so_count:-0} native libs (expected >=35 from rockbox-y1 base)" >&2
            errors=$((errors + 1))
        fi
    fi

    if [ ! -f "$sys_mount/etc/solar/disable-rockbox-for-solar.sh" ]; then
        echo "audit fail: /system/etc/solar/disable-rockbox-for-solar.sh missing" >&2
        errors=$((errors + 1))
    elif ! grep -q 'pm disable' "$sys_mount/etc/solar/disable-rockbox-for-solar.sh" 2>/dev/null; then
        echo "audit fail: disable-rockbox-for-solar.sh must pm disable org.rockbox" >&2
        errors=$((errors + 1))
    elif ! grep -q 'packages -d' "$sys_mount/etc/solar/disable-rockbox-for-solar.sh" 2>/dev/null; then
        echo "audit fail: disable-rockbox-for-solar.sh must gate marker on Rockbox disabled state" >&2
        errors=$((errors + 1))
    fi

    if [ ! -f "$sys_mount/etc/solar/switch-to-stock.sh" ]; then
        echo "audit fail: /system/etc/solar/switch-to-stock.sh missing" >&2
        errors=$((errors + 1))
    elif grep -qiE '(^|[[:space:]]|/)reboot\b|reboot -p|/system/bin/reboot' \
            "$sys_mount/etc/solar/switch-to-stock.sh" 2>/dev/null; then
        echo "audit fail: switch-to-stock.sh must not reboot (unified keymap)" >&2
        errors=$((errors + 1))
    elif ! grep -q 'verify_rockbox_disabled' "$sys_mount/etc/solar/switch-to-stock.sh" 2>/dev/null; then
        echo "audit fail: switch-to-stock.sh must verify Rockbox disabled before enabling Solar" >&2
        errors=$((errors + 1))
    fi

    if [ ! -f "$sys_mount/etc/solar/sync-rockbox-libs.sh" ]; then
        echo "audit fail: /system/etc/solar/sync-rockbox-libs.sh missing (Rockbox codec sync)" >&2
        errors=$((errors + 1))
    fi

    if [ ! -f "$sys_mount/etc/solar/sync-y1-keymap.sh" ]; then
        echo "audit fail: /system/etc/solar/sync-y1-keymap.sh missing (unified keymap sync)" >&2
        errors=$((errors + 1))
    elif [ ! -f "$sys_mount/etc/solar/Y1-Rockbox.kl" ] \
            && [ ! -f "$sys_mount/etc/solar/Y2-Rockbox.kl" ]; then
        echo "audit fail: /system/etc/solar/Y1-Rockbox.kl or Y2-Rockbox.kl missing" >&2
        errors=$((errors + 1))
    fi

    if [ -f "$sys_mount/etc/solar/Y2-Rockbox.kl" ]; then
        canon_kl="$sys_mount/etc/solar/Y2-Rockbox.kl"
        canon_name="Y2-Rockbox.kl"
    else
        canon_kl="$sys_mount/etc/solar/Y1-Rockbox.kl"
        canon_name="Y1-Rockbox.kl"
    fi

    if [ ! -f "$sys_mount/usr/keylayout/Generic.kl" ] || [ ! -f "$sys_mount/usr/keylayout/Rockbox.kl" ]; then
        echo "audit fail: keylayout files missing" >&2
        errors=$((errors + 1))
    elif ! cmp -s "$sys_mount/usr/keylayout/Generic.kl" "$canon_kl"; then
        echo "audit fail: Generic.kl is not identical to $canon_name (wheel 126/127)" >&2
        errors=$((errors + 1))
    elif ! cmp -s "$sys_mount/usr/keylayout/Stock.kl" "$canon_kl"; then
        echo "audit fail: Stock.kl must match $canon_name (unified keymap)" >&2
        errors=$((errors + 1))
    elif ! cmp -s "$sys_mount/usr/keylayout/Rockbox.kl" "$canon_kl"; then
        echo "audit fail: Rockbox.kl must match $canon_name (unified keymap)" >&2
        errors=$((errors + 1))
    elif [ ! -f "$sys_mount/usr/keylayout/$canon_name" ]; then
        echo "audit fail: /system/usr/keylayout/$canon_name missing" >&2
        errors=$((errors + 1))
    elif ! cmp -s "$sys_mount/usr/keylayout/$canon_name" "$canon_kl"; then
        echo "audit fail: usr/keylayout/$canon_name must match etc/solar copy" >&2
        errors=$((errors + 1))
    elif [ -f "$sys_mount/usr/keylayout/mtk-tpd-kpd.kl" ] && [ -f "$sys_mount/usr/keylayout/mtk-kpd.kl" ] \
            && ! cmp -s "$sys_mount/usr/keylayout/mtk-tpd-kpd.kl" "$sys_mount/usr/keylayout/mtk-kpd.kl"; then
        echo "audit fail: mtk-tpd-kpd.kl must match mtk-kpd.kl (side keys 88/87, wheel 126/127)" >&2
        errors=$((errors + 1))
    fi

    if [ ! -f "$sys_mount/etc/init.d/99Y1ButtonScript" ]; then
        echo "audit fail: 99Y1ButtonScript missing (Back+Play Rockbox gesture)" >&2
        errors=$((errors + 1))
    fi

    if [ ! -f "$sys_mount/media/bootanimation.zip" ]; then
        echo "audit fail: /system/media/bootanimation.zip missing" >&2
        errors=$((errors + 1))
    fi

    if [ ! -f "$sys_mount/bin/bootanimation" ]; then
        echo "audit fail: /system/bin/bootanimation missing" >&2
        errors=$((errors + 1))
    fi

    if [ ! -f "$base_dir/boot.img" ]; then
        echo "audit fail: boot.img missing from ROM archive" >&2
        errors=$((errors + 1))
    fi

    if [ ! -f "$base_dir/logo.bin" ]; then
        echo "audit fail: logo.bin missing from ROM archive" >&2
        errors=$((errors + 1))
    fi

    if [ -n "$(find "$user_mount" -maxdepth 1 -name '*_launcher.apk' ! -name 'com.solar.launcher.apk' -print -quit 2>/dev/null)" ]; then
        echo "audit fail: legacy launcher APK in userdata" >&2
        errors=$((errors + 1))
    fi

    if find "$user_mount" -maxdepth 1 -name 'com.innioasis.*.apk' 2>/dev/null | grep -q .; then
        echo "audit fail: stock Innioasis launcher APK present in userdata" >&2
        errors=$((errors + 1))
    fi

    if [ ! -f "$user_mount/data/switch-to-stock.sh" ]; then
        echo "audit fail: userdata/data/switch-to-stock.sh missing (Rockbox launcher handoff)" >&2
        errors=$((errors + 1))
    elif ! cmp -s "$user_mount/data/switch-to-stock.sh" "$sys_mount/etc/solar/switch-to-stock.sh"; then
        echo "audit fail: userdata switch-to-stock.sh must match /system/etc/solar copy" >&2
        errors=$((errors + 1))
    elif grep -qiE '(^|[[:space:]]|/)reboot\b|reboot -p|/system/bin/reboot' \
            "$user_mount/data/switch-to-stock.sh" 2>/dev/null; then
        echo "audit fail: userdata switch-to-stock.sh must not reboot" >&2
        errors=$((errors + 1))
    fi

    if [ "$errors" -ne 0 ]; then
        die "ROM audit failed with $errors error(s)"
    fi

    echo "==> ROM audit passed"
}

SOLAR_ROM_BUILD_DIR="${SOLAR_ROM_BUILD_DIR:-$HOME/.cache/solar-rom-build}"
mkdir -p "$SOLAR_ROM_BUILD_DIR"
WORK_DIR="$(mktemp -d "$SOLAR_ROM_BUILD_DIR/work-XXXXXX")"
BASE_DIR="$WORK_DIR/base"
MOUNT_SYS="$BASE_DIR/mount_sys"
MOUNT_USER="$BASE_DIR/mount_user"
STAGING_APK="$WORK_DIR/solar.apk"

mkdir -p "$BASE_DIR" "$MOUNT_SYS" "$MOUNT_USER"

if [ -n "$SOLAR_APK" ]; then
    [ -f "$SOLAR_APK" ] || die "APK not found: $SOLAR_APK"
    cp "$SOLAR_APK" "$STAGING_APK"
    echo "==> Using local Solar APK: $SOLAR_APK"
elif [ -n "$SOLAR_APK_URL" ]; then
    echo "==> Downloading Solar APK from release metadata"
    download_solar_apk "$STAGING_APK" "$SOLAR_TAG" "$SOLAR_APK_URL"
elif [ -n "$SOLAR_TAG" ]; then
    echo "==> Downloading Solar APK for tag ${SOLAR_TAG}"
    download_solar_apk "$STAGING_APK" "$SOLAR_TAG"
else
    echo "==> Downloading latest Solar release APK"
    download_solar_apk "$STAGING_APK"
fi

echo "==> Downloading type-${TYPE} base firmware"
curl -fsSL -o "$BASE_DIR/rom.zip" "$BASE_URL"
unzip -q "$BASE_DIR/rom.zip" -d "$BASE_DIR"
normalize_firmware_layout

[ -f "$BASE_DIR/system.img" ] || die "system.img not found under $BASE_DIR after unzip"
[ -f "$BASE_DIR/userdata.img" ] || die "userdata.img not found under $BASE_DIR after unzip"

# Y2: keep pristine sparse copies of partitions MTK DA is sensitive about (userdata repack, boot mix-ups).
PRISTINE_DIR="$WORK_DIR/pristine"
if [ "$TYPE" = "y2" ]; then
    mkdir -p "$PRISTINE_DIR"
    for _pristine in boot.img recovery.img cache.img secro.img lk.bin MBR EBR1 \
            preloader_eastaeon82_wet_kk.bin userdata.img system.img; do
        [ -f "$BASE_DIR/$_pristine" ] && cp -a "$BASE_DIR/$_pristine" "$PRISTINE_DIR/$_pristine"
    done
fi

SYSTEM_MOUNT_SRC="$(prepare_image_for_mount "$BASE_DIR/system.img")"
USERDATA_MOUNT_SRC="$(prepare_image_for_mount "$BASE_DIR/userdata.img")"

echo "==> Mounting system.img and userdata.img"
sudo modprobe loop 2>/dev/null || true
sudo mount -t ext4 -o loop "$SYSTEM_MOUNT_SRC" "$MOUNT_SYS"
sudo mount -t ext4 -o loop "$USERDATA_MOUNT_SRC" "$MOUNT_USER"

echo "==> Patching system partition"
while IFS= read -r apk; do
    [ -n "$apk" ] || continue
    echo "  removing $apk"
    sudo rm -f "$apk"
done < <(find "$MOUNT_SYS/priv-app" -iname '*innioasis*' 2>/dev/null || true)

if [ "$TYPE" = "y2" ]; then
    while IFS= read -r apk; do
        [ -n "$apk" ] || continue
        echo "  removing $apk"
        sudo rm -f "$apk" "${apk%.apk}.odex"
    done < <(find "$MOUNT_SYS/priv-app" -iname '*factorylauncher*' 2>/dev/null || true)
else
    for apk in "$MOUNT_SYS/app"/com.*.apk; do
        [ -e "$apk" ] || continue
        base=$(basename "$apk")
        [ "$base" = "$SYSTEM_APK_NAME" ] && continue
        echo "  removing system/app/$base"
        sudo rm -f "$apk"
    done
fi

if [ "$TYPE" = "y2" ]; then
    install_rockbox_from_y1_base "$MOUNT_SYS"
fi

# Keep org.rockbox.apk + librockbox.so from base firmware for launcher switching.
sudo rm -f "$MOUNT_SYS/etc/init.d/99Y1LauncherInit.sh"

sudo mkdir -p "$MOUNT_SYS/app" "$MOUNT_SYS/usr/keylayout"
sudo cp "$STAGING_APK" "$MOUNT_SYS/app/$SYSTEM_APK_NAME"
sudo chmod 644 "$MOUNT_SYS/app/$SYSTEM_APK_NAME"
sudo chown root:root "$MOUNT_SYS/app/$SYSTEM_APK_NAME"

echo "==> Install TLS prep (Conscrypt JNI + modern CA roots)"
TLS_STAGE="$WORK_DIR/system-tls"
chmod +x "$REPO_ROOT/scripts/stage-y1-system-prep.sh" "$REPO_ROOT/scripts/apply-y1-system-prep.sh"
"$REPO_ROOT/scripts/stage-y1-system-prep.sh" "$TLS_STAGE" "$STAGING_APK" "$REPO_ROOT"
sudo "$REPO_ROOT/scripts/apply-y1-system-prep.sh" "$TLS_STAGE" "$MOUNT_SYS"
sudo chown root:root "$MOUNT_SYS/lib/libconscrypt_jni.so"
sudo chown root:root "$MOUNT_SYS/etc/security/cacerts"/*.0 2>/dev/null || true

echo "==> Install Solar boot init (SD library folders + TLS sanity)"
sudo mkdir -p "$MOUNT_SYS/etc/init.d"
sudo cp "$REPO_ROOT/solar-rom/system/99SolarInit.sh" "$MOUNT_SYS/etc/init.d/99SolarInit.sh"
sudo chmod 755 "$MOUNT_SYS/etc/init.d/99SolarInit.sh"
sudo chown root:root "$MOUNT_SYS/etc/init.d/99SolarInit.sh"

echo "==> Install launcher switch scripts + unified Rockbox keymap"
sudo mkdir -p "$MOUNT_SYS/etc/solar"
sudo cp "$SCRIPT_DIR/switch-to-stock.sh" "$MOUNT_SYS/etc/solar/switch-to-stock.sh"
sudo cp "$SCRIPT_DIR/switch-to-rockbox.sh" "$MOUNT_SYS/etc/solar/switch-to-rockbox.sh"
sudo cp "$SCRIPT_DIR/sync-rockbox-libs.sh" "$MOUNT_SYS/etc/solar/sync-rockbox-libs.sh"
sudo cp "$SCRIPT_DIR/sync-y1-keymap.sh" "$MOUNT_SYS/etc/solar/sync-y1-keymap.sh"
sudo cp "$SCRIPT_DIR/disable-rockbox-for-solar.sh" "$MOUNT_SYS/etc/solar/disable-rockbox-for-solar.sh"
sudo cp "$SCRIPT_DIR/solar-usb-recovery-agent.sh" "$MOUNT_SYS/etc/solar/solar-usb-recovery-agent.sh"
sudo chmod 755 "$MOUNT_SYS/etc/solar/switch-to-stock.sh" "$MOUNT_SYS/etc/solar/switch-to-rockbox.sh" \
    "$MOUNT_SYS/etc/solar/sync-rockbox-libs.sh" "$MOUNT_SYS/etc/solar/sync-y1-keymap.sh" \
    "$MOUNT_SYS/etc/solar/disable-rockbox-for-solar.sh" \
    "$MOUNT_SYS/etc/solar/solar-usb-recovery-agent.sh"
sudo chown root:root "$MOUNT_SYS/etc/solar/switch-to-stock.sh" "$MOUNT_SYS/etc/solar/switch-to-rockbox.sh" \
    "$MOUNT_SYS/etc/solar/sync-rockbox-libs.sh" "$MOUNT_SYS/etc/solar/sync-y1-keymap.sh" \
    "$MOUNT_SYS/etc/solar/disable-rockbox-for-solar.sh" \
    "$MOUNT_SYS/etc/solar/solar-usb-recovery-agent.sh"

sudo cp "$REPO_ROOT/solar-rom/system/99Y1ButtonScript" "$MOUNT_SYS/etc/init.d/99Y1ButtonScript"
sudo chmod 755 "$MOUNT_SYS/etc/init.d/99Y1ButtonScript"
sudo chown root:root "$MOUNT_SYS/etc/init.d/99Y1ButtonScript"

[ -f "$SCRIPT_DIR/Y1-Rockbox.kl" ] || die "missing $SCRIPT_DIR/Y1-Rockbox.kl"
# ponytail: Y1 uses Y1-Rockbox.kl; Y2 uses Y2-Rockbox.kl (same wheel/side map, Y2 volume on 114/115).
if [ "$TYPE" = "y2" ]; then
    CANONICAL_KL="$SCRIPT_DIR/Y2-Rockbox.kl"
    CANONICAL_KL_NAME="Y2-Rockbox.kl"
    [ -f "$CANONICAL_KL" ] || die "missing $CANONICAL_KL"
else
    CANONICAL_KL="$SCRIPT_DIR/Y1-Rockbox.kl"
    CANONICAL_KL_NAME="Y1-Rockbox.kl"
fi
sudo cp "$CANONICAL_KL" "$MOUNT_SYS/etc/solar/$CANONICAL_KL_NAME"
sudo chmod 644 "$MOUNT_SYS/etc/solar/$CANONICAL_KL_NAME"
sudo chown root:root "$MOUNT_SYS/etc/solar/$CANONICAL_KL_NAME"
for _kl in Stock.kl Rockbox.kl Generic.kl "$CANONICAL_KL_NAME"; do
    sudo cp "$CANONICAL_KL" "$MOUNT_SYS/usr/keylayout/$_kl"
done
sudo chmod 644 "$MOUNT_SYS/usr/keylayout/Stock.kl" "$MOUNT_SYS/usr/keylayout/Rockbox.kl" \
    "$MOUNT_SYS/usr/keylayout/Generic.kl" "$MOUNT_SYS/usr/keylayout/$CANONICAL_KL_NAME"
sudo chown root:root "$MOUNT_SYS/usr/keylayout/Stock.kl" "$MOUNT_SYS/usr/keylayout/Rockbox.kl" \
    "$MOUNT_SYS/usr/keylayout/Generic.kl" "$MOUNT_SYS/usr/keylayout/$CANONICAL_KL_NAME"
# Patch wheel scancodes in mtk-kpd.kl without replacing GPIO key map; mtk-tpd-kpd uses the same file.
# ponytail: sed -i writes a temp file beside the target — needs sudo on mounted system.img (root-owned dir).
if [ -f "$MOUNT_SYS/usr/keylayout/mtk-kpd.kl" ]; then
    sudo sed -i 's/^key 105[[:space:]].*/key 105   MEDIA_PLAY/' "$MOUNT_SYS/usr/keylayout/mtk-kpd.kl"
    sudo sed -i 's/^key 106[[:space:]].*/key 106   MEDIA_PAUSE/' "$MOUNT_SYS/usr/keylayout/mtk-kpd.kl"
    if [ "$TYPE" = "y2" ]; then
        # Y2 stock mtk-kpd maps side keys to DPAD — unify to MEDIA_NEXT/PREVIOUS like Y1.
        sudo sed -i 's/^key 163[[:space:]].*/key 163   MEDIA_NEXT/' "$MOUNT_SYS/usr/keylayout/mtk-kpd.kl"
        sudo sed -i 's/^key 165[[:space:]].*/key 165   MEDIA_PREVIOUS/' "$MOUNT_SYS/usr/keylayout/mtk-kpd.kl"
    fi
    sudo cp "$MOUNT_SYS/usr/keylayout/mtk-kpd.kl" "$MOUNT_SYS/usr/keylayout/mtk-tpd-kpd.kl"
    sudo chmod 644 "$MOUNT_SYS/usr/keylayout/mtk-kpd.kl" "$MOUNT_SYS/usr/keylayout/mtk-tpd-kpd.kl"
    sudo chown root:root "$MOUNT_SYS/usr/keylayout/mtk-kpd.kl" "$MOUNT_SYS/usr/keylayout/mtk-tpd-kpd.kl"
fi

# Koensayr AVRCP patches target Y1 MT6572 MtkBt.odex/mtkbt layout. Y2 ATA (MT6582)
# ships a different BT stack (517 KiB odex, no libextavrcp_jni.so) — patch sites do not match.
if [ "$TYPE" = "y2" ]; then
    echo "==> Skipping AVRCP stack patches (Y2 base — Y1-only mtkbt/MtkBt.odex layout)"
else
    echo "==> AVRCP Bluetooth stack (Y1Bridge + mtkbt patches; hardware keylayout unchanged)"
    chmod +x "$SCRIPT_DIR/apply-avrcp-patches.sh"
    sudo "$SCRIPT_DIR/apply-avrcp-patches.sh" "$MOUNT_SYS"
fi

install_solar_boot_assets "$BASE_DIR" "$MOUNT_SYS"

if [ "$TYPE" = "y2" ]; then
    echo "==> Install Y1 permissive su (Y2 stock base is unrooted; no Superuser.apk prompt)"
    chmod +x "$SCRIPT_DIR/install-y1-su-system.sh"
    sudo "$SCRIPT_DIR/install-y1-su-system.sh" "$MOUNT_SYS"
fi

echo "==> Patching userdata partition"
while IFS= read -r apk; do
    [ -n "$apk" ] || continue
    echo "  removing userdata/$(basename "$apk")"
    sudo rm -f "$apk"
done < <(find "$MOUNT_USER" -maxdepth 1 -name 'com.innioasis.*.apk' 2>/dev/null || true)
sudo rm -f "$MOUNT_USER/data/com.innioasis.y1.apk"
sudo rm -f "$MOUNT_USER/data/com.innioasis.y2.apk"
sudo rm -f "$MOUNT_USER"/*_launcher.apk
sudo rm -f "$MOUNT_USER/data/*_launcher_initialized"
sudo rm -f "$MOUNT_USER/data/.solar_rom_home_ready"
sudo rm -f "$MOUNT_USER/data/initialized"

echo "==> Seed Rockbox switch scripts in userdata (overwrite rockbox-y1 reboot/keylayout script)"
sudo mkdir -p "$MOUNT_USER/data"
sudo cp "$SCRIPT_DIR/switch-to-stock.sh" "$MOUNT_USER/data/switch-to-stock.sh"
sudo cp "$SCRIPT_DIR/switch-to-rockbox.sh" "$MOUNT_USER/data/switch-to-rockbox.sh"
sudo chmod 755 "$MOUNT_USER/data/switch-to-stock.sh" "$MOUNT_USER/data/switch-to-rockbox.sh"
sudo chown root:root "$MOUNT_USER/data/switch-to-stock.sh" "$MOUNT_USER/data/switch-to-rockbox.sh"

audit_rom_contents "$BASE_DIR" "$MOUNT_SYS" "$MOUNT_USER"

echo "==> Unmounting images"
sync
sudo umount "$MOUNT_SYS"
sudo umount "$MOUNT_USER"
MOUNT_SYS=""
MOUNT_USER=""

if [ "$TYPE" = "y2" ]; then
    # MTK SP Flash on MT6582: desparsed ext4 images flash slower but avoid sparse chunk DA failures.
    finalize_image_raw "$BASE_DIR/system.img" "$SYSTEM_MOUNT_SRC"
    finalize_image_raw "$BASE_DIR/userdata.img" "$USERDATA_MOUNT_SRC"
    for _restore in boot.img recovery.img cache.img secro.img lk.bin MBR EBR1 \
            preloader_eastaeon82_wet_kk.bin; do
        [ -f "$PRISTINE_DIR/$_restore" ] && cp -a "$PRISTINE_DIR/$_restore" "$BASE_DIR/$_restore"
    done
else
    finalize_image_after_mount "$BASE_DIR/system.img" "$SYSTEM_MOUNT_SRC"
    finalize_image_after_mount "$BASE_DIR/userdata.img" "$USERDATA_MOUNT_SRC"
fi

rm -f "$BASE_DIR/rom.zip"
rm -rf "$MOUNT_SYS" "$MOUNT_USER"

mkdir -p "$(dirname "$OUTPUT")"
echo "==> Creating $OUTPUT"
rm -f "$OUTPUT"
(
    cd "$BASE_DIR"
    zip -j -q "$OUTPUT" ./*
)

if [ -n "${SUDO_UID:-}" ] && [ -n "${SUDO_GID:-}" ]; then
    chown "$SUDO_UID:$SUDO_GID" "$OUTPUT"
fi

echo "==> Built $OUTPUT ($(du -h "$OUTPUT" | awk '{print $1}'))"
