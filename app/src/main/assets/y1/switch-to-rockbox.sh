#!/system/bin/sh
# Rockbox entry — delegate to switch-to-stock.sh with --rockbox (same script Solar uses).
exec sh "$(dirname "$0")/switch-to-stock.sh" --rockbox
