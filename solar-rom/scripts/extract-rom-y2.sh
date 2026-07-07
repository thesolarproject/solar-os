#!/usr/bin/env bash
# Unpack rom_y2.zip for SP Flash Tool directory mode (.rom_y2_extracted).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ZIP="${1:-$REPO_ROOT/rom_y2.zip}"
OUT="${2:-$REPO_ROOT/.rom_y2_extracted}"

die() { echo "error: $*" >&2; exit 1; }

[ -f "$ZIP" ] || die "missing $ZIP — build with: ./solar-rom/scripts/build-rom.sh y2 --apk app/build/outputs/apk/release/app-release.apk rom_y2.zip"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
chmod +x "$SCRIPT_DIR/verify-y2-rom-flash.sh"
"$SCRIPT_DIR/verify-y2-rom-flash.sh" "$ZIP"

rm -rf "$OUT"
mkdir -p "$OUT"
unzip -q -o "$ZIP" -d "$OUT"
touch "$OUT/.extract_complete"

echo "==> Extracted to $OUT"
ls -lh "$OUT/system.img" "$OUT/userdata.img" "$OUT/boot.img" "$OUT/MT6582_Android_scatter.txt"
file "$OUT/system.img" "$OUT/userdata.img"
