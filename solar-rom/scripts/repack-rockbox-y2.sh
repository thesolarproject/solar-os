#!/usr/bin/env bash
# Deprecated alias — use patch-rockbox-y2.sh (applies su -c + sharedUserId strip + sign).
exec "$(dirname "$0")/patch-rockbox-y2.sh" "$@"
