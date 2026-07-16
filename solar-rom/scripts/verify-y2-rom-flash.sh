#!/usr/bin/env bash
# 2026-07-06 — Pre-flash sanity check for rom_y2.zip (MT6582 / MT6582_Android_scatter.txt).
# Ensures images work with MTKclient scatter wo + SP Flash Tool (flashing tools).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MANIFEST="${SCRIPT_DIR}/../config/y2-mtk-flash-manifest.txt"
ZIP="${1:-rom_y2.zip}"
die() { echo "error: $*" >&2; exit 1; }

[ -f "$ZIP" ] || die "usage: $0 [rom_y2.zip]"
[ -f "$MANIFEST" ] || die "missing $MANIFEST"

require_cmd() { command -v "$1" >/dev/null 2>&1 || die "missing $1"; }
require_cmd unzip

is_sparse() {
    local magic
    magic=$(dd if="$1" bs=1 count=4 2>/dev/null | od -An -tx1 | tr -d ' \n')
    [ "$magic" = "3aff26ed" ]
}

scatter_linear_addr() {
    local scatter="$1"
    local part_name="$2"
    awk -v name="$part_name" '
        $0 ~ "partition_name: " name "$" { found=1; next }
        found && $0 ~ /linear_start_addr:/ {
            sub(/.*linear_start_addr:[[:space:]]*/, "")
            print
            exit
        }
    ' "$scatter"
}

echo "==> Verify $ZIP ($(du -h "$ZIP" | awk '{print $1}'))"

tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

required=(
    MT6582_Android_scatter.txt
    preloader_eastaeon82_wet_kk.bin
    MBR EBR1 EBR2
    logo.bin lk.bin boot.img recovery.img system.img userdata.img
)
unzip -q "$ZIP" "${required[@]}" -d "$tmpdir"

for f in "${required[@]}"; do
    [ -f "$tmpdir/$f" ] || die "missing $f in zip (required for MTKclient + SP Flash Y2 install)"
done

boot_sz=$(stat -c%s "$tmpdir/boot.img")
if [ "$boot_sz" -lt 5700000 ] || [ "$boot_sz" -gt 5900000 ]; then
    die "boot.img is ${boot_sz} bytes (expected MT6582 stock ~5773312 — reject Y1 boot.img)"
fi

sys_path="$tmpdir/system.img"
user_path="$tmpdir/userdata.img"

require_cmd tune2fs

if is_sparse "$sys_path"; then
    die "system.img is Android sparse — rebuild rom_y2.zip (Y2 ships desparsed ext4 for MTKclient wo)"
fi
if is_sparse "$user_path"; then
    die "userdata.img is Android sparse — rebuild rom_y2.zip (MTKclient wo needs raw ext4, not sparse container)"
fi

tune2fs -l "$sys_path" >/dev/null || die "system.img is not valid ext4"
tune2fs -l "$user_path" >/dev/null || die "userdata.img is not valid ext4"

sys_uuid=$(tune2fs -l "$sys_path" 2>/dev/null | awk '/Filesystem UUID/ {print $3}')
user_uuid=$(tune2fs -l "$user_path" 2>/dev/null | awk '/Filesystem UUID/ {print $3}')
if [ -n "$sys_uuid" ] && [ "$sys_uuid" = "$user_uuid" ]; then
    die "system.img and userdata.img share UUID $sys_uuid — run tune2fs -U random on one image before flash"
fi

sys_bytes=$(stat -c%s "$sys_path")
user_bytes=$(stat -c%s "$user_path")
if [ "$sys_bytes" -lt 400000000 ]; then
    die "system.img is only ${sys_bytes} bytes — expected desparsed ext4 ~800MB+ after Solar bake"
fi
if [ "$user_bytes" -lt 500000000 ]; then
    die "userdata.img is only ${user_bytes} bytes — expected desparsed ext4 ~800MB (sparse zip is ~15MB)"
fi
# 2026-07-06 — USRDATA partition_size in MT6582 scatter is 0x32000000; raw image must not exceed EMMC slot.
USRDATA_PARTITION_MAX=$((0x32000000))
if [ "$user_bytes" -gt "$USRDATA_PARTITION_MAX" ]; then
    die "userdata.img is ${user_bytes} bytes — exceeds USRDATA partition ceiling 0x32000000 (${USRDATA_PARTITION_MAX})"
fi

scatter="$tmpdir/MT6582_Android_scatter.txt"
# 2026-07-16 — Reject EBR2-as-partition map that shifts ANDROID +0x80000 (remote CI bootloop).
if grep -q 'partition_name: EBR2' "$scatter"; then
    die "scatter declares EBR2 as a partition (shifts ANDROID/USRDATA — known Y2 bootloop; use stock ATA scatter)"
fi
android_addr=$(scatter_linear_addr "$scatter" "ANDROID")
if [ "$(printf '%s' "$android_addr" | tr 'A-F' 'a-f')" = "0x6580000" ]; then
    die "scatter ANDROID is 0x6580000 (shifted EBR2 layout) — stock ATA is 0x6500000"
fi
if [ "$(printf '%s' "$android_addr" | tr 'A-F' 'a-f')" != "0x6500000" ]; then
    die "scatter ANDROID linear_start_addr is $android_addr (expected stock 0x6500000)"
fi
while IFS= read -r line || [ -n "$line" ]; do
    line="${line%%#*}"
    line="${line#"${line%%[![:space:]]*}"}"
    [ -n "$line" ] || continue
    fname="${line%%:*}"
    expected="${line#*:}"
    case "$fname" in
        logo.bin) scatter_part=LOGO ;;
        lk.bin) scatter_part=UBOOT ;;
        boot.img) scatter_part=BOOTIMG ;;
        recovery.img) scatter_part=RECOVERY ;;
        system.img) scatter_part=ANDROID ;;
        userdata.img) scatter_part=USRDATA ;;
        *) continue ;;
    esac
    actual=$(scatter_linear_addr "$scatter" "$scatter_part")
    [ -n "$actual" ] || die "scatter missing linear_start_addr for $scatter_part ($fname)"
    if [ "$(printf '%s' "$actual" | tr 'A-F' 'a-f')" != "$(printf '%s' "$expected" | tr 'A-F' 'a-f')" ]; then
        die "scatter $scatter_part linear_start_addr is $actual (expected $expected for MTKclient wo)"
    fi
done < "$MANIFEST"

echo "  boot.img        ${boot_sz} bytes (MT6582 stock)"
echo "  system.img      ${sys_bytes} bytes (desparsed ext4, $(du -h "$sys_path" | awk '{print $1}'))"
echo "  userdata.img    ${user_bytes} bytes (desparsed ext4, $(du -h "$user_path" | awk '{print $1}'))"
echo "  flash bundle    MBR EBR1 EBR2 preloader + MT6582 scatter offsets OK"
echo "==> OK for MTKclient scatter wo + SP Flash Tool (MT6582_Android_scatter.txt)"
