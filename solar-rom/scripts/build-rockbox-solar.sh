#!/usr/bin/env bash
# Build org.rockbox.apk for Solar ROM — source patches + optional native build; apktool fallback.
# Usage: build-rockbox-solar.sh OUTPUT.apk [y1|y2]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUT="${1:-}"
TARGET="${2:-y2}"
KS="${SOLAR_PLATFORM_KEYSTORE:-$REPO_ROOT/.gradle/platform.keystore}"

die() { echo "error: $*" >&2; exit 1; }

[[ -n "$OUT" ]] || die "usage: $0 OUTPUT.apk [y1|y2]"
[[ -f "$KS" ]] || die "missing keystore $KS"

PATCH_APK="$SCRIPT_DIR/patch-rockbox-y2.sh"
FETCH_SRC="$SCRIPT_DIR/fetch-rockbox-y1-source.sh"
FETCH_ASSETS="$SCRIPT_DIR/fetch-rockbox-y1-y2-assets.sh"

WORK="$(mktemp -d "${TMPDIR:-/tmp}/rockbox-solar-build-XXXXXX")"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT

# --- Source tree optional full build (manifest-only Y2 strip when source build enabled) ---
SRC_ROOT="$("$FETCH_SRC")"
BUILD_TREE="$WORK/rockbox-src"
echo "==> Copy rockbox-y1 source to build tree"
cp -a "$SRC_ROOT" "$BUILD_TREE"

if [[ "$TARGET" == "y2" ]]; then
    if grep -q 'android:sharedUserId' "$BUILD_TREE/android/AndroidManifest.xml" 2>/dev/null; then
        sed -i 's/ android:sharedUserId="[^"]*"//g' "$BUILD_TREE/android/AndroidManifest.xml"
        echo "==> Stripped sharedUserId from source AndroidManifest.xml"
    fi
fi

try_source_apk_build() {
    [[ "${SOLAR_ROCKBOX_SOURCE_BUILD:-0}" == "1" ]] || return 1
    [[ -n "${ANDROID_NDK_PATH:-}" ]] || return 1
    [[ -n "${ANDROID_SDK_PATH:-}" ]] || return 1
    command -v make >/dev/null 2>&1 || return 1
    echo "==> Full rockbox-y1 source build (SOLAR_ROCKBOX_SOURCE_BUILD=1)"
    (
        cd "$BUILD_TREE/android"
        if [[ ! -x installToolchain.sh ]]; then return 1; fi
        [[ -d "$HOME/android-ndk-r10e" ]] || ./installToolchain.sh
        export ANDROID_SDK_PATH="${ANDROID_SDK_PATH:-$HOME/android-sdk}"
        export ANDROID_NDK_PATH="${ANDROID_NDK_PATH:-$HOME/android-ndk-r10e}"
        mkdir -p build && cd build
        ../../tools/configure --target=201 --lcdwidth=480 --lcdheight=360 --type=n
        make -j"$(nproc 2>/dev/null || echo 2)"
        make classes zip unsigned-apk
        [[ -f rockbox_unsigned.apk ]]
    )
}

BASE_APK=""
if try_source_apk_build; then
    BASE_APK="$BUILD_TREE/android/build/rockbox_unsigned.apk"
    echo "==> Source build produced $BASE_APK"
else
    echo "==> Source build skipped — using rockbox-y1 type-A base APK + patches"
    CACHE="$("$FETCH_ASSETS")"
    BASE_APK="$CACHE/org.rockbox.apk"
    [[ -f "$BASE_APK" ]] || die "missing base org.rockbox.apk"
fi

if [[ "$TARGET" == "y2" ]]; then
    "$PATCH_APK" "$BASE_APK" "$OUT" "$KS"
else
    cp "$BASE_APK" "$WORK/unsigned.apk"
    if command -v apksigner >/dev/null 2>&1; then
        apksigner sign --ks "$KS" --ks-pass pass:android --ks-key-alias platform \
            --key-pass pass:android --out "$OUT" "$WORK/unsigned.apk"
    else
        cp "$WORK/unsigned.apk" "$OUT"
    fi
fi

echo "==> Rockbox Solar APK ($TARGET): $OUT"
