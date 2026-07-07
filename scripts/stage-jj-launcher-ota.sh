#!/usr/bin/env bash
# 2026-07-06 — Stage JJ Launcher APK as jj_latest.apk for solar-update Pages hosting.
# Layman: copy a built JJ APK so publish can put it at thesolarproject.github.io/solar-update/jj_latest.apk
# Usage: stage-jj-launcher-ota.sh [path-to-jj.apk]
# Default source: reference/jj_launcher/app/build/outputs/apk/release/app-release.apk
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${1:-$ROOT/reference/jj_launcher/app/build/outputs/apk/release/app-release.apk}"
DEST="$ROOT/build-ota-staging/jj_latest.apk"
if [[ ! -f "$SRC" ]]; then
  echo "JJ APK not found: $SRC" >&2
  echo "Build reference/jj_launcher first, or pass an APK path." >&2
  exit 1
fi
mkdir -p "$(dirname "$DEST")"
cp -f "$SRC" "$DEST"
echo "Staged $DEST"
echo "Publish to solar-update repo root as jj_latest.apk (GitHub Pages)."
