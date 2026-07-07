#!/usr/bin/env bash
# Optional: compile Xposed native app_process via XposedTools + full AOSP trees.
# CI uses vendored solar-rom/vendor/xposed/ instead — run this only when MTK stock
# app_process causes boot loops with rovo89 generic binaries.
#
# Prerequisites (documented, not automated):
#   - Perl + XposedTools: https://github.com/rovo89/XposedTools
#   - AOSP 4.2 tree (API 17) and/or 4.4 tree (API 19) with Xposed sources integrated
#   - build.conf pointing at AOSP roots
#
# Usage: build-xposed-native.sh arm:17|arm:19
set -euo pipefail

TARGET="${1:?usage: build-xposed-native.sh arm:17|arm:19}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENDOR="$SCRIPT_DIR/../vendor/xposed"
XPOSED_TOOLS="${XPOSED_TOOLS_DIR:-$HOME/aosp/XposedTools}"

die() { echo "build-xposed-native: $*" >&2; exit 1; }

[ -d "$XPOSED_TOOLS" ] || die "missing XposedTools at $XPOSED_TOOLS (set XPOSED_TOOLS_DIR)"
[ -f "$XPOSED_TOOLS/build.pl" ] || die "missing $XPOSED_TOOLS/build.pl"
[ -f "$XPOSED_TOOLS/build.conf" ] || die "copy build.conf.sample -> build.conf and configure AOSP paths"

SDK="${TARGET#*:}"
ARCH="${TARGET%%:*}"
OUT="$VENDOR/api${SDK}-arm"

echo "==> build-xposed-native: $TARGET -> $OUT"
(cd "$XPOSED_TOOLS" && ./build.pl -t "$ARCH:$SDK")

# build.pl output layout varies — copy known artifacts when present.
BUILT="$XPOSED_TOOLS/out" 
[ -d "$BUILT" ] || die "no output dir after build.pl (check build.conf outdir)"

mkdir -p "$OUT"
[ -f "$BUILT/system/bin/app_process" ] && cp "$BUILT/system/bin/app_process" "$OUT/app_process"
[ -f "$BUILT/java/XposedBridge.jar" ] && cp "$BUILT/java/XposedBridge.jar" "$OUT/XposedBridge.jar"
[ -f "$BUILT/xposed.prop" ] && cp "$BUILT/xposed.prop" "$OUT/xposed.prop"

[ -f "$OUT/app_process" ] || die "build did not produce app_process — check XposedTools logs"
echo "==> copied to $OUT — verify on hardware before committing"
