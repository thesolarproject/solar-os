#!/usr/bin/env bash
# 2026-07-07 — Fetch AOSP framework tags for hook validation (Y1=api17, Y2=api19).
# Layman: downloads reference Android source so we can check hook targets exist before patching.
# Technical: shallow git clone of platform/frameworks/base at jb-mr1.1-release / kk-release tags.
# Reversal: delete vendor/aosp-reference/; hooks use try/catch fail-open only.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="${ROOT}/solar-rom/vendor/aosp-reference"
Y1_TAG="android-4.2.2_r1"
Y2_TAG="android-4.4.2_r1"
REPO="https://android.googlesource.com/platform/frameworks/base"

usage() {
  echo "Usage: $0 [--y1|--y2|--both] [--dest DIR]"
  echo "  Fetches AOSP frameworks/base reference trees for hook decompile cross-check."
  exit 1
}

TARGET="both"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --y1) TARGET="y1"; shift ;;
    --y2) TARGET="y2"; shift ;;
    --both) TARGET="both"; shift ;;
    --dest) DEST="$2"; shift 2 ;;
    -h|--help) usage ;;
    *) echo "Unknown arg: $1"; usage ;;
  esac
done

fetch_tag() {
  local name="$1"
  local tag="$2"
  local dir="${DEST}/${name}"
  if [[ -d "${dir}/.git" ]]; then
    echo "==> ${name}: already cloned at ${dir} (git pull --ff-only)"
    git -C "${dir}" fetch --depth 1 origin "refs/tags/${tag}" 2>/dev/null || true
    git -C "${dir}" checkout -q "${tag}" 2>/dev/null || git -C "${dir}" checkout -q FETCH_HEAD
    return 0
  fi
  mkdir -p "${dir}"
  echo "==> ${name}: shallow clone ${tag} -> ${dir}"
  git clone --depth 1 --branch "${tag}" "${REPO}" "${dir}" 2>/dev/null || {
    echo "    Tag clone failed; trying init + fetch tag..."
    git clone --depth 1 "${REPO}" "${dir}"
    git -C "${dir}" fetch --depth 1 origin "refs/tags/${tag}"
    git -C "${dir}" checkout -q "${tag}"
  }
}

mkdir -p "${DEST}"

case "${TARGET}" in
  y1) fetch_tag "api17-jb" "${Y1_TAG}" ;;
  y2) fetch_tag "api19-kk" "${Y2_TAG}" ;;
  both)
    fetch_tag "api17-jb" "${Y1_TAG}"
    fetch_tag "api19-kk" "${Y2_TAG}"
    ;;
esac

cat <<EOF

Done. Reference trees under: ${DEST}

Hook validation workflow:
  1. Extract framework.jar from ROM system.img (Y1 and Y2 separately)
  2. Decompile with jadx; grep target class (MenuDialogHelper, PhoneWindowManager, Toast)
  3. Cross-check method signatures against vendor/aosp-reference/
  4. Implement hook with try/catch fail-open (AppMenuHooks pattern)
  5. adb install bridge APK → reboot → audit-device-parity.sh

See docs/developers/building-on-y1-y2.md § Framework diff before new hooks.

EOF
