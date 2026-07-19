#!/usr/bin/env bash
# 2026-07-19 — Rockbox platform bundle retired (Solar-only). Fail if stale tree returns.
# Was: required org.rockbox-*.apk under assets/platform/rockbox/. Reversal: restore checks from git.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RB="$ROOT/app/src/main/assets/platform/rockbox"
if [ -d "$RB" ]; then
  echo "verify-platform-rockbox-assets: stale $RB — re-run sync-platform-assets.sh" >&2
  exit 1
fi
echo "verify-platform-rockbox-assets: OK (no platform/rockbox bundle)"
