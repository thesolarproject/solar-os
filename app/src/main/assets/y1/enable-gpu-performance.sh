#!/system/bin/sh
# 2026-07-05 — Force GPU rendering + disable HW overlays on Y1/Y2 (JB/KK developer-options parity).
# Layman: ask Android to draw menus with the GPU and skip overlay shortcuts that glitch on MTK.
# Technical: settings force_gpu_rendering + SurfaceFlinger transaction 1008 (disable overlays).
# Tradeoff: disable overlays can cost battery on stock Android — intentional for Solar overlay stability.
# Reversal: remove from 99SolarInit.sh; delete GraphicsPerformancePolicy calls in Solar app.

SETTINGS_DB="/data/data/com.android.providers.settings/databases/settings.db"

apply_setting() {
    local table="$1"
    local key="$2"
    local val="$3"
    if command settings >/dev/null 2>&1; then
        settings put "$table" "$key" "$val" 2>/dev/null || true
    fi
    if [ -f "$SETTINGS_DB" ] && command sqlite3 >/dev/null 2>&1; then
        sqlite3 "$SETTINGS_DB" \
            "INSERT OR REPLACE INTO $table (name,value) VALUES ('$key','$val');" 2>/dev/null || true
    fi
}

# Force GPU rendering — try global + system keys (OEM naming varies on API 17/19).
apply_setting system force_gpu_rendering 1
apply_setting global force_gpu_rendering 1
apply_setting system force_hw_ui 1
apply_setting global force_hw_ui 1

# Disable HW overlays — SurfaceFlinger API (AOSP HardwareOverlaysPreferenceController code 1008).
if command service >/dev/null 2>&1; then
    service call SurfaceFlinger 1008 i32 1 >/dev/null 2>&1 || true
fi

apply_setting global disable_overlays 1
apply_setting system disable_overlays 1
