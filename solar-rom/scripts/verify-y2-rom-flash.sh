#!/usr/bin/env bash
# Pre-flash sanity check for rom_y2.zip (MT6582 / MT6582_Android_scatter.txt).
set -euo pipefail

ZIP="${1:-rom_y2.zip}"
die() { echo "error: $*" >&2; exit 1; }

[ -f "$ZIP" ] || die "usage: $0 [rom_y2.zip]"

require_cmd() { command -v "$1" >/dev/null 2>&1 || die "missing $1"; }
require_cmd unzip

is_sparse() {
    local magic
    magic=$(dd if="$1" bs=1 count=4 2>/dev/null | od -An -tx1 | tr -d ' \n')
    [ "$magic" = "3aff26ed" ]
}

echo "==> Verify $ZIP ($(du -h "$ZIP" | awk '{print $1}'))"

tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT
unzip -q "$ZIP" MT6582_Android_scatter.txt boot.img system.img userdata.img -d "$tmpdir"

[ -f "$tmpdir/MT6582_Android_scatter.txt" ] || die "missing MT6582_Android_scatter.txt"

boot_sz=$(stat -c%s "$tmpdir/boot.img")
if [ "$boot_sz" -lt 5700000 ] || [ "$boot_sz" -gt 5900000 ]; then
    die "boot.img is ${boot_sz} bytes (expected MT6582 stock ~5773312 — reject Y1 boot.img)"
fi

sys_path="$tmpdir/system.img"
user_path="$tmpdir/userdata.img"

if is_sparse "$sys_path"; then
    require_cmd simg2img
    require_cmd tune2fs
    sys_raw="$tmpdir/system.raw"
    simg2img "$sys_path" "$sys_raw"
    tune2fs -l "$sys_raw" >/dev/null || die "system.img sparse round-trip failed"
    sys_kind="sparse"
    sys_detail="$(du -h "$sys_raw" | awk '{print $1}') ext4 expanded"
else
    require_cmd tune2fs
    tune2fs -l "$sys_path" >/dev/null || die "system.img is not valid ext4"
    sys_kind="desparsed ext4"
    sys_detail="$(du -h "$sys_path" | awk '{print $1}')"
fi

if is_sparse "$user_path"; then
    require_cmd simg2img
    require_cmd tune2fs
    user_raw="$tmpdir/user.raw"
    simg2img "$user_path" "$user_raw"
    tune2fs -l "$user_raw" >/dev/null || die "userdata.img sparse round-trip failed"
    user_kind="sparse"
    user_detail="$(du -h "$user_raw" | awk '{print $1}') ext4 expanded"
    if [ "$(stat -c%s "$user_path")" -lt 5000000 ]; then
        die "userdata.img sparse is only $(stat -c%s "$user_path") bytes — re-extract from a fresh rom_y2.zip build"
    fi
else
    require_cmd tune2fs
    tune2fs -l "$user_path" >/dev/null || die "userdata.img is not valid ext4"
    user_kind="desparsed ext4"
    user_detail="$(du -h "$user_path" | awk '{print $1}')"
fi

echo "  boot.img        ${boot_sz} bytes (MT6582 stock)"
echo "  system.img      $(stat -c%s "$sys_path") bytes ($sys_kind, $sys_detail)"
echo "  userdata.img    $(stat -c%s "$user_path") bytes ($user_kind, $user_detail)"

if [ "$sys_kind" = "sparse" ]; then
    echo "warn: system.img is still sparse — rebuild rom_y2.zip (Y2 now ships desparsed ext4 by default)"
fi

echo "==> OK for SP Flash Tool (scatter: MT6582_Android_scatter.txt from zip or extract dir)"
