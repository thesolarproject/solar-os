#!/system/bin/sh
# 2026-07-05 — Idempotent /system file staging from extracted platform-prep cache.
# Usage: solar-platform-prep.sh copy <src> <dest> <mode>
# Emits: SOLAR_PREP_PROGRESS pct message
progress() { echo "SOLAR_PREP_PROGRESS $1 $2"; }
case "$1" in
copy)
  SRC="$2"; DEST="$3"; MODE="${4:-644}"
  [ -f "$SRC" ] || exit 1
  mount -o remount,rw /system 2>/dev/null || true
  mkdir -p "$(dirname "$DEST")"
  cp "$SRC" "$DEST" || exit 1
  chmod "$MODE" "$DEST" 2>/dev/null || true
  progress 100 "copied $(basename "$DEST")"
  ;;
*) exit 1 ;;
esac
