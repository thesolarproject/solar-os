#!/usr/bin/env bash
# 2026-07-05 — Release APK build entry; stamps version from SOURCE_DATE_EPOCH at build start.
# APK/ROM parity: sync-platform-assets.sh runs before Gradle so bundled platform kit matches ROM.
# When changing: keep sync-platform-assets.sh call; bump prepVersion in sync script for ladder changes.
# Reversal: remove sync-platform-assets.sh call; APK ships stale or missing Xposed repair assets.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh"
cd "$ROOT"
export SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-$(date -u +%s)}"
CATALOG="$ROOT/catalog/artist-separators.csv"
ASSET="$ROOT/app/src/main/assets/artist-separators.csv"
if [[ -f "$CATALOG" ]]; then
  cp "$CATALOG" "$ASSET"
fi
chmod +x gradlew scripts/ensure-platform-keystore.sh \
  solar-rom/scripts/sync-y1-assets.sh solar-rom/scripts/verify-y1-assets.sh \
  solar-rom/scripts/sync-platform-assets.sh solar-rom/scripts/verify-platform-assets.sh
./scripts/ensure-platform-keystore.sh
./solar-rom/scripts/sync-y1-assets.sh
./solar-rom/scripts/sync-platform-assets.sh
./gradlew assembleRelease "$@"

SIGNED="$ROOT/app/build/outputs/apk/release/app-release.apk"
[[ -f "$SIGNED" ]] || { echo "ERROR: missing $SIGNED" >&2; exit 1; }
echo "Signed (AOSP platform key): $SIGNED"
