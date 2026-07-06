#!/usr/bin/env bash
# 2026-07-06 — Repair Y2 boot after MTKclient flash: rewrite userdata (+ optional system) from rom_y2.zip.
# Stuck bootanim + adb shell "sh failed" usually means /data or /system ext4 did not mount (bad sparse write or duplicate UUID).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MANIFEST="${SCRIPT_DIR}/../config/y2-mtk-flash-manifest.txt"
ZIP="${1:-$REPO_ROOT/dist/rom_y2.zip}"
OUT="${2:-$REPO_ROOT/.y2_mtk_recovery}"
MTK_PY="${MTK_PY:-}"
PARTS="${PARTS:-userdata system}"

die() { echo "error: $*" >&2; exit 1; }

[ -f "$ZIP" ] || die "missing $ZIP"
[ -f "$MANIFEST" ] || die "missing $MANIFEST"
command -v tune2fs >/dev/null 2>&1 || die "missing tune2fs"
command -v simg2img >/dev/null 2>&1 || die "missing simg2img (android-sdk-libsparse-utils)"

is_sparse() {
    local magic
    magic=$(dd if="$1" bs=1 count=4 2>/dev/null | od -An -tx1 | tr -d ' \n')
    [ "$magic" = "3aff26ed" ]
}

prepare_raw_image() {
    local src="$1"
    local dest="$2"
    if is_sparse "$src"; then
        echo "==> Expanding sparse $(basename "$src") for MTKclient wo"
        simg2img "$src" "$dest"
    else
        cp -a "$src" "$dest"
    fi
    tune2fs -U random "$dest" >/dev/null
}

scatter_offset() {
    local fname="$1"
    awk -F: -v f="$fname" '$1 == f {print $2; exit}' "$MANIFEST" | tr -d ' '
}

mkdir -p "$OUT"
echo "==> Extract flash images from $ZIP"
unzip -q -o "$ZIP" system.img userdata.img -d "$OUT"
[ -f "$OUT/system.img" ] && [ -f "$OUT/userdata.img" ] || die "zip missing system.img or userdata.img"

SYS_RAW="$OUT/system_mtk_write.img"
USER_RAW="$OUT/userdata_mtk_write.img"
prepare_raw_image "$OUT/system.img" "$SYS_RAW"
prepare_raw_image "$OUT/userdata.img" "$USER_RAW"

sys_uuid=$(tune2fs -l "$SYS_RAW" | awk '/Filesystem UUID/ {print $3}')
user_uuid=$(tune2fs -l "$USER_RAW" | awk '/Filesystem UUID/ {print $3}')
echo "  system UUID   $sys_uuid"
echo "  userdata UUID $user_uuid"
[ "$sys_uuid" != "$user_uuid" ] || die "UUID collision after repair — report bug"

write_cmds=()
for part in $PARTS; do
    case "$part" in
        system)
            img="$SYS_RAW"
            addr=$(scatter_offset system.img)
            ;;
        userdata)
            img="$USER_RAW"
            addr=$(scatter_offset userdata.img)
            ;;
        *)
            die "unknown part: $part (use: userdata system)"
            ;;
    esac
    [ -n "$addr" ] || die "no manifest offset for $part"
    sz=$(stat -c%s "$img")
    write_cmds+=("wo $addr $(printf '0x%x' "$sz") $(basename "$img")")
done

echo "==> Recovery writes (power off Y2, connect USB in preloader mode):"
echo "    export MTK_PY=/path/to/mtk.py   # MTKclient checkout"
echo "    cd $OUT"
multi="python3 \"\$MTK_PY\" multi $(IFS=';'; echo "${write_cmds[*]}")"
echo "    $multi"

if [ "${RUN_MTK:-0}" = "1" ]; then
    [ -n "$MTK_PY" ] && [ -f "$MTK_PY" ] || die "set MTK_PY to mtk.py from your MTKclient checkout"
    cd "$OUT"
    # shellcheck disable=SC2086
    python3 "$MTK_PY" multi "$(IFS=';'; echo "${write_cmds[*]}")"
fi
