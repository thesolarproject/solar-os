#!/usr/bin/env bash
# 2026-07-06 — Stage Rockbox Y1 OTA artifacts for solar-update Pages hosting.
# Layman: copy rockbox-y1 release files so publish can ship rb_y1_latest, rockbox.apk, update.zip.
# Technical: update.zip layout matches rockbox-y1 android/scripts/update.sh (libs + rockbox.apk only).
# Usage: stage-rockbox-y1-ota.sh [path-to-rockbox.apk]
# Default: download github.com/rockbox-y1/rockbox/releases/latest/download/rockbox.apk
# Publish build-ota-staging/{rb_y1_latest.apk,rockbox.apk,update.zip} to solar-update repo root.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST_DIR="$ROOT/build-ota-staging"
GITHUB_URL="https://github.com/rockbox-y1/rockbox/releases/latest/download/rockbox.apk"
SRC="${1:-}"

if [[ -z "$SRC" ]]; then
  SRC="$DEST_DIR/_rockbox_src.apk"
  mkdir -p "$DEST_DIR"
  echo "==> Downloading $GITHUB_URL"
  curl -fsSL -o "$SRC" "$GITHUB_URL"
fi

if [[ ! -f "$SRC" ]]; then
  echo "Rockbox APK not found: $SRC" >&2
  exit 1
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

mkdir -p "$DEST_DIR" "$WORK/update/libs/armeabi"
cp -f "$SRC" "$DEST_DIR/rb_y1_latest.apk"
cp -f "$SRC" "$DEST_DIR/rockbox.apk"
cp -f "$SRC" "$WORK/update/rockbox.apk"

echo "==> Extracting lib/armeabi/*.so for update.zip"
unzip -o -q "$SRC" 'lib/armeabi/*.so' -d "$WORK/apk"
mv "$WORK/apk/lib/armeabi/"*.so "$WORK/update/libs/armeabi/"

[ -f "$WORK/update/libs/armeabi/librockbox.so" ] \
  || { echo "missing librockbox.so in APK" >&2; exit 1; }

( cd "$WORK" && zip -qr "$DEST_DIR/update.zip" update )

echo "Staged:"
echo "  $DEST_DIR/rb_y1_latest.apk"
echo "  $DEST_DIR/rockbox.apk"
echo "  $DEST_DIR/update.zip"
echo "Publish all three to thesolarproject.github.io/solar-update/ on each Solar release."
