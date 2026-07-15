#!/usr/bin/env bash
# Keep app-bundled Y1 assets identical to ROM sources (switch scripts for runtime root patch).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$ROOT/solar-rom/scripts"
DST="$ROOT/app/src/main/assets/y1"
mkdir -p "$DST"
# 2026-07-08 — solar-launcher-exec.sh added; it had drifted (stock/Innioasis switch support
# existed only in the APK asset copy, so fresh ROM flashes shipped the older script).
for f in switch-to-stock.sh switch-to-rockbox.sh sync-rockbox-libs.sh sync-rockbox-assets.sh sync-y1-keymap.sh disable-rockbox-for-solar.sh apply-preferred-home-boot.sh solar-launcher-exec.sh disable-large-font-accessibility.sh enable-gpu-performance.sh solar-usb-recovery-agent.sh Y1-Rockbox.kl Y2-Rockbox.kl mtk-kpd.y1.stock.kl; do
    cp "$SRC/$f" "$DST/$f"
done
chmod +x "$ROOT/solar-rom/scripts/verify-y1-assets.sh"
"$ROOT/solar-rom/scripts/verify-y1-assets.sh"
