#!/usr/bin/env bash
# 2026-07-06 — One-shot FM Now Playing placeholder: dark grey + Aura FM Radio icon.
# Layman: baked album art so the player does not draw icons at runtime.
# Technical: composites themes/default/FM Radio_YS.png onto #222222 360×360 PNG.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ICON="$ROOT/app/src/main/assets/themes/default/FM Radio_YS.png"
OUT="$ROOT/app/src/main/res/drawable-nodpi/radio_fm_np_placeholder.png"
python3 - "$ICON" "$OUT" <<'PY'
import sys
from PIL import Image

icon_path, out_path = sys.argv[1], sys.argv[2]
size = 360
bg = Image.new("RGB", (size, size), (34, 34, 34))
icon = Image.open(icon_path).convert("RGBA")
target_w = int(size * 0.55)
scale = target_w / icon.width
icon = icon.resize((target_w, int(icon.height * scale)), Image.LANCZOS)
x = (size - icon.width) // 2
y = (size - icon.height) // 2
bg.paste(icon, (x, y), icon)
bg.save(out_path, "PNG")
print(out_path)
PY
