#!/usr/bin/env bash
# Dump Y1 keylayout + org.rockbox state for Solar Y1KeyMap verification.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh"

classify_layout() {
    local l103="$1"
    local l105="$2"
    if [[ "$l105" == *MEDIA_PREVIOUS* ]]; then
        echo "Stock Y1 (wheel 21/22, media skip 88/87)"
    elif [[ "$l103" == *MEDIA_PREVIOUS* ]]; then
        echo "Rockbox sideload swap (wheel 88/87, skip 21/22)"
    elif [[ "$l105" == *DPAD_UP* && "$l103" == *DPAD_LEFT* ]]; then
        echo "Rockbox ROM variant (wheel 19/20, skip 21/22)"
    elif [[ "$l103" == *DPAD_UP* ]]; then
        echo "Rockbox classic / canonical (wheel 19/20, skip 21/22)"
    else
        echo "Unknown — check mtk-kpd.kl manually"
    fi
}

echo "== Y1 input verification =="
if ! adb get-state >/dev/null 2>&1; then
    echo "No adb device — connect Y1 to dump live keylayout"
    echo ""
    echo "Reference layouts in repo:"
    echo "  Stock sideload: solar-rom/scripts/mtk-kpd.kl"
    echo "  Rockbox ROM:    solar-rom/scripts/mtk-kpd-rockbox.kl"
    grep -E '^key (103|105|106|108)' "$ROOT/solar-rom/scripts/mtk-kpd.kl" || true
    echo "---"
    grep -E '^key (103|105|106|108)' "$ROOT/solar-rom/scripts/mtk-kpd-rockbox.kl" || true
    exit 0
fi

echo ""
echo "-- mtk-kpd.kl scancodes 103–106 (wheel + prev/next) --"
L103="$(adb shell "grep '^key 103' /system/usr/keylayout/mtk-kpd.kl" 2>/dev/null | tr -d '\r' | head -1)"
L105="$(adb shell "grep '^key 105' /system/usr/keylayout/mtk-kpd.kl" 2>/dev/null | tr -d '\r' | head -1)"
adb shell "grep -E '^key (103|105|106|108)' /system/usr/keylayout/mtk-kpd.kl" 2>/dev/null | tr -d '\r' || echo "(mtk-kpd.kl not readable)"

echo ""
echo "-- Generic.kl scancodes 103–106 (must match mtk-kpd on ROM) --"
run_adb shell "grep -E '^key (103|105|106|108)' /system/usr/keylayout/Generic.kl" | tr -d '\r' || echo "(Generic.kl not readable)"

echo ""
echo "-- Detected layout (Y1KeyMap rules) --"
classify_layout "$L103" "$L105"

echo ""
echo "-- org.rockbox package --"
if adb shell pm path org.rockbox 2>/dev/null | tr -d '\r' | grep -q .; then
    adb shell pm list packages -e 2>/dev/null | tr -d '\r' | grep org.rockbox || adb shell pm path org.rockbox 2>/dev/null | tr -d '\r'
else
    echo "(org.rockbox not installed)"
fi

echo ""
echo "-- Solar rockboxKeymap pref --"
run_adb_pref="$(adb shell "run-as com.solar.launcher cat /data/data/com.solar.launcher/shared_prefs/solar.xml 2>/dev/null" \
    | tr -d '\r' | grep -E 'rockbox_keymap|rockbox_keymap_manual' || true)"
if [[ -n "$run_adb_pref" ]]; then
    echo "$run_adb_pref"
else
    echo "(pref not readable — app may use auto-detect defaults)"
fi

echo ""
echo "-- Expected Solar 1.0 behavior --"
echo "| Role            | Stock keycodes | Rockbox keycodes |"
echo "| Wheel up/down   | 21 / 22        | 19 / 20          |"
echo "| Media prev/next | 88 / 87        | 21 / 22          |"
echo "| BT remotes      | MEDIA_* keys   | same             |"
echo ""
echo "Settings -> Debug -> Rockbox button mapping shows layout label when toggled manually."

if [[ "${1:-}" == "--getevent" ]]; then
    echo ""
    echo "-- getevent (5s sample — spin wheel / press prev/next) --"
    timeout 5 adb shell getevent -l 2>/dev/null || true
fi

echo ""
echo "Done. Compare keycodes: adb logcat -s SolarKoensayr MainActivity"
