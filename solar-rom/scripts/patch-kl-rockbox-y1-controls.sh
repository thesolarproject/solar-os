#!/usr/bin/env bash
# Patch Stock.kl-style Y1 control scancodes to Rockbox-Y1 mapping in a keylayout file.
# ponytail: Generic.kl must match mtk-kpd.kl or InputReader emits stock 21/22 while app detects Rockbox.
# Usage: patch-kl-rockbox-y1-controls.sh FILE [FILE...]   (use --sudo for ROM mount paths)
set -euo pipefail

USE_SUDO=0
FILES=()
for arg in "$@"; do
    if [[ "$arg" == "--sudo" ]]; then USE_SUDO=1
    else FILES+=("$arg"); fi
done
[[ ${#FILES[@]} -gt 0 ]] || { echo "usage: $0 [--sudo] FILE [FILE...]" >&2; exit 1; }

run_sed() {
    if [[ "$USE_SUDO" -eq 1 ]]; then sudo sed -i "$@"
    else sed -i "$@"; fi
}

for f in "${FILES[@]}"; do
    [[ -f "$f" ]] || { echo "error: missing $f" >&2; exit 1; }
    run_sed \
        -e 's/^key 103[[:space:]].*/key 103   DPAD_UP/' \
        -e 's/^key 108[[:space:]].*/key 108   DPAD_DOWN/' \
        -e 's/^key 105[[:space:]].*/key 105   DPAD_LEFT/' \
        -e 's/^key 106[[:space:]].*/key 106   DPAD_RIGHT/' \
        "$f"
done
