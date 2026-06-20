#!/usr/bin/env bash
# Extract stock /system files from Rockbox-Y1 type-A base for AVRCP asset staging.
# Usage: extract-avrcp-stock-sys.sh OUTPUT_DIR
set -euo pipefail

OUT="${1:-}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE="${SOLAR_ROM_BASE_CACHE:-$ROOT/.ci-cache/rockbox-bases}"
BASE_ZIP="$CACHE/type-a-base.rom.zip"
WORK=""

die() { echo "error: $*" >&2; exit 1; }

cleanup() { [[ -n "$WORK" && -d "$WORK" ]] && rm -rf "$WORK"; }
trap cleanup EXIT

[[ -n "$OUT" ]] || die "usage: $0 OUTPUT_DIR"
command -v debugfs >/dev/null 2>&1 || die "debugfs required (e2fsprogs)"
command -v unzip >/dev/null 2>&1 || die "unzip required"
command -v curl >/dev/null 2>&1 || die "curl required"

mkdir -p "$CACHE" "$OUT"
if [[ ! -s "$BASE_ZIP" ]]; then
    echo "==> Downloading Rockbox type-A base"
    curl -fsSL -o "$BASE_ZIP.part" \
        "https://github.com/rockbox-y1/rockbox/releases/download/type-a-base/rom.zip"
    mv "$BASE_ZIP.part" "$BASE_ZIP"
fi

WORK="$(mktemp -d)"
unzip -q "$BASE_ZIP" system.img -d "$WORK"
[[ -f "$WORK/system.img" ]] || die "system.img missing in type-A base"

PATHS=(
    app/MtkBt.odex
    bin/mtkbt
    lib/libextavrcp_jni.so
    lib/libextavrcp.so
    lib/libaudio.a2dp.default.so
    usr/keylayout/AVRCP.kl
    etc/bluetooth/audio.conf
    etc/bluetooth/auto_pairing.conf
    etc/bluetooth/blacklist.conf
    build.prop
)

echo "==> Extracting stock system files from type-A base"
for rel in "${PATHS[@]}"; do
    mkdir -p "$OUT/$(dirname "$rel")"
    if debugfs -R "dump /$rel $OUT/$rel" "$WORK/system.img" 2>/dev/null; then
        echo "  $rel"
    else
        echo "  skip (missing in base): $rel"
    fi
done
echo "==> Stock system tree: $OUT"
