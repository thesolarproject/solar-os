#!/usr/bin/env bash
# Patch rockbox-y1 org.rockbox.apk for Y2: manifest-only sharedUserId strip + platform sign.
# Java/native compat (Connectivity.execShell, daemonsu) lives in SolarContextBridgeY2 RockboxCompatHooks.
# Native system() shims patch staged librockbox.so after extract-rockbox-staged-assets.sh.
# Usage: patch-rockbox-y2.sh INPUT.apk OUTPUT.apk [KEYSTORE]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
IN="${1:-}"
OUT="${2:-}"
KS="${3:-$REPO_ROOT/.gradle/platform.keystore}"

die() { echo "error: $*" >&2; exit 1; }

[ -n "$IN" ] && [ -f "$IN" ] || die "usage: $0 INPUT.apk OUTPUT.apk [KEYSTORE]"
[ -n "$OUT" ] || die "usage: $0 INPUT.apk OUTPUT.apk [KEYSTORE]"
[ -f "$KS" ] || die "missing keystore $KS — run scripts/ensure-platform-keystore.sh"

command -v java >/dev/null 2>&1 || die "missing java"
APKTOOL="${APKTOOL_JAR:-$SCRIPT_DIR/apktool.jar}"
if [ ! -f "$APKTOOL" ]; then
    echo "==> Downloading apktool.jar"
    curl -fsSL -o "$APKTOOL" \
        "https://github.com/iBotPeaches/Apktool/releases/download/v2.9.3/apktool_2.9.3.jar"
fi

find_build_tool() {
    local name="$1"
    if command -v "$name" >/dev/null 2>&1; then command -v "$name"; return; fi
    if [ -n "${ANDROID_HOME:-}" ]; then
        local p
        p="$(find "$ANDROID_HOME/build-tools" -name "$name" 2>/dev/null | sort -V | tail -1)"
        [ -n "$p" ] && echo "$p" && return
    fi
    die "missing $name (set ANDROID_HOME or add build-tools to PATH)"
}

ZIPALIGN="$(find_build_tool zipalign)"
APKSIGNER="$(find_build_tool apksigner)"

IN_SIZE="$(stat -c%s "$IN" 2>/dev/null || stat -f%z "$IN")"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/rockbox-y2-patch-XXXXXX")"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT

echo "==> Decompile manifest/resources only (-s keeps classes.dex + lib/*.so pristine)"
java -jar "$APKTOOL" d -f -s -o "$WORK/src" "$IN"

# Y2 PackageManager rejects rockbox-y1 sharedUserId without matching MTK platform cert.
if grep -q 'android:sharedUserId' "$WORK/src/AndroidManifest.xml"; then
    sed -i 's/ android:sharedUserId="[^"]*"//g' "$WORK/src/AndroidManifest.xml"
    echo "==> Stripped android:sharedUserId from manifest"
fi

echo "==> Rebuild unsigned APK"
java -jar "$APKTOOL" b "$WORK/src" -o "$WORK/unsigned.apk"

UNSIGNED_SIZE="$(stat -c%s "$WORK/unsigned.apk" 2>/dev/null || stat -f%z "$WORK/unsigned.apk")"
[ "${UNSIGNED_SIZE:-0}" -gt 1000000 ] || die "rebuilt APK too small (${UNSIGNED_SIZE:-0} bytes)"

# Fail CI if apktool rebuild shrinks suspiciously vs input.
MIN_SIZE=$((IN_SIZE * 95 / 100))
[ "$UNSIGNED_SIZE" -ge "$MIN_SIZE" ] || die "rebuilt APK too small vs input ($UNSIGNED_SIZE < $MIN_SIZE)"

ALIGNED="$WORK/aligned.apk"
"$ZIPALIGN" -f 4 "$WORK/unsigned.apk" "$ALIGNED"

echo "==> Sign with Solar platform key"
"$APKSIGNER" sign \
    --ks "$KS" \
    --ks-pass pass:android \
    --ks-key-alias platform \
    --key-pass pass:android \
    --out "$OUT" \
    "$ALIGNED"

"$APKSIGNER" verify --verbose "$OUT" >/dev/null

# Audit: libmisc.so present (asset bootstrap zip inside APK).
unzip -l "$OUT" 2>/dev/null > "$WORK/ziplist.txt" || true
grep -q 'libmisc\.so' "$WORK/ziplist.txt" \
    || die "patched APK missing lib/armeabi/libmisc.so"

# Audit: no sharedUserId left in manifest.
if unzip -p "$OUT" AndroidManifest.xml 2>/dev/null | grep -q 'sharedUserId'; then
    die "patched manifest still has sharedUserId"
fi

# Audit: APK librockbox.so still has stock am-start strings (native patch is staged-only).
APK_LIB="$WORK/apk-librockbox.so"
unzip -o -q -p "$OUT" lib/armeabi/librockbox.so > "$APK_LIB" 2>/dev/null || true
if [ -s "$APK_LIB" ]; then
    # 2026-07-05: no -q — grep -q early-exit + pipefail SIGPIPE'd strings and could mask a match.
    if strings "$APK_LIB" 2>/dev/null | grep 'solar-rb-launch' >/dev/null; then
        die "APK librockbox.so must stay pristine — solar-rb-launch belongs in staged copy only"
    fi
fi

echo "==> Patched Rockbox for Y2 (manifest-only): $OUT ($(du -h "$OUT" | awk '{print $1}'))"
