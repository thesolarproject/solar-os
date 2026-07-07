#!/usr/bin/env bash
# 2026-07-07 — Fetch AOSP framework reference trees for Y1 (4.2.2) and Y2 (4.4.2) hook development.
# Layman: download the Android source slices we diff before writing Xposed hooks.
# Technical: shallow git clone of tagged releases into solar-rom/vendor/aosp-reference/.
# Reversal: delete vendor/aosp-reference/ — hooks still ship in bridge APKs.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/vendor/aosp-reference"
mkdir -p "$DEST"

clone_slice() {
  local tag="$1"
  local name="$2"
  local target="$DEST/$name"
  if [ -d "$target/.git" ]; then
    echo "skip $name — already cloned at $target"
    return 0
  fi
  echo "cloning $tag -> $target (frameworks/base only, depth 1)..."
  git clone --depth 1 --branch "$tag" \
    https://android.googlesource.com/platform/frameworks/base "$target"
}

# API 17 — Innioasis Y1 (Android 4.2.2)
clone_slice "android-4.2.2_r1" "api17-frameworks-base"

# API 19 — Innioasis Y2 (Android 4.4.2)
clone_slice "android-4.4.2_r1" "api19-frameworks-base"

cat <<EOF

AOSP reference trees ready under:
  $DEST/api17-frameworks-base  (Y1 hooks — Toast, MenuDialogHelper, PhoneWindowManager)
  $DEST/api19-frameworks-base  (Y2 hooks — verify method signatures before bridge build)

Compare MTK ROM jars from system.img against these tags — MTK forks diverge.
See docs/developers/building-on-y1-y2.md § Framework diff before new hooks.

EOF
