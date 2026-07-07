#!/system/bin/sh
# 2026-07-05 — Reset Accessibility “Large fonts” on Y1/Y2 (Android 4.2.2 / 4.4.4).
# Layman: keep system text at normal size so Solar’s 480×360 UI fits the wheel layout.
# Reversal: remove this call from 99SolarInit.sh to let users keep large fonts.

NORMAL_SCALE="1.0"
FONT_KEY="font_scale"
SETTINGS_DB="/data/data/com.android.providers.settings/databases/settings.db"

# Prefer settings CLI when present (KitKat).
if command settings >/dev/null 2>&1; then
    settings put system "$FONT_KEY" "$NORMAL_SCALE" 2>/dev/null
fi

# JB/KK fallback — update system table directly (init.d runs as root).
if [ -f "$SETTINGS_DB" ]; then
    sqlite3 "$SETTINGS_DB" "INSERT OR REPLACE INTO system (name,value) VALUES ('$FONT_KEY','$NORMAL_SCALE');" 2>/dev/null
fi

# content:// fallback for builds without sqlite3 in PATH.
if command content >/dev/null 2>&1; then
    content update --uri content://settings/system \
        --bind value:s:"$NORMAL_SCALE" \
        --where "name='$FONT_KEY'" 2>/dev/null \
        || content insert --uri content://settings/system \
            --bind name:s:"$FONT_KEY" --bind value:s:"$NORMAL_SCALE" 2>/dev/null
fi
