#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh"
cd "$ROOT"
chmod +x gradlew

stage_koensayr_assets() {
  local k="$ROOT/solar-rom/koensayr"
  [[ -d "$k/src/patches" ]] || return 0
  local assets="$ROOT/app/src/main/assets/koensayr"
  local source_sys="${KOENSAYR_SOURCE_SYS:-}"
  if [[ -z "$source_sys" ]]; then
    source_sys="$ROOT/build/koensayr-stock-system"
    if [[ ! -f "$source_sys/build.prop" ]]; then
      chmod +x "$ROOT/scripts/extract-koensayr-stock-sys.sh"
      if ! "$ROOT/scripts/extract-koensayr-stock-sys.sh" "$source_sys"; then
        echo "WARN: Koensayr asset staging skipped (set KOENSAYR_SOURCE_SYS or connect device)" >&2
        return 0
      fi
    fi
  fi
  chmod +x "$ROOT/scripts/stage-koensayr-prep.sh" "$ROOT/solar-rom/scripts/koensayr-apply-to-tree.sh"
  local staging="$ROOT/build/koensayr-apk-assets/system"
  KOENSAYR_SOURCE_SYS="$source_sys" "$ROOT/scripts/stage-koensayr-prep.sh" "$staging"
  rm -rf "$assets"
  mkdir -p "$assets"
  cp -a "$staging"/* "$assets/"
  echo "==> Koensayr APK assets staged ($assets)"
}

stage_koensayr_assets
./gradlew assembleRelease "$@"

UNSIGNED="$ROOT/app/build/outputs/apk/release/app-release-unsigned.apk"
SIGNED="$ROOT/app/build/outputs/apk/release/app-release.apk"
KEY_PK8="$SOLAR_PLATFORM_KEY_PK8"
KEY_PEM="$SOLAR_PLATFORM_KEY_PEM"

APKSIGNER="${ANDROID_HOME}/build-tools/35.0.0/apksigner"
if [[ ! -x "$APKSIGNER" && -d "${ANDROID_HOME}/build-tools" ]]; then
  APKSIGNER="$(ls -1 "${ANDROID_HOME}/build-tools"/*/apksigner 2>/dev/null | tail -1)"
fi
[[ -n "$APKSIGNER" && -x "$APKSIGNER" ]] || {
  echo "ERROR: apksigner not found under ANDROID_HOME/build-tools" >&2
  exit 1
}
[[ -f "$KEY_PK8" && -f "$KEY_PEM" ]] || {
  echo "ERROR: platform keys missing (set SOLAR_PLATFORM_KEY_PK8 / SOLAR_PLATFORM_KEY_PEM)" >&2
  exit 1
}
[[ -f "$UNSIGNED" ]] || {
  echo "ERROR: missing $UNSIGNED" >&2
  exit 1
}

echo "== Sign release APK with AOSP platform key =="
"$APKSIGNER" sign --key "$KEY_PK8" --cert "$KEY_PEM" --out "$SIGNED" "$UNSIGNED"
"$APKSIGNER" verify --verbose "$SIGNED" >/dev/null
echo "Signed: $SIGNED"
