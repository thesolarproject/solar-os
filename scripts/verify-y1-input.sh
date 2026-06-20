#!/usr/bin/env bash
# Dump Y1 keylayout + org.rockbox state for Solar Y1KeyMap verification.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh"

EXIT_CODE=0

classify_layout() {
    local gen103="$1"
    local mtk105="$2"
    local gen114="${3:-}"
    if [[ "$gen103" == *MEDIA_PLAY* ]]; then
        echo "Rockbox-y1 Rockbox.kl (wheel 126/127 MEDIA_PLAY/PAUSE, skip 88/87)"
    elif [[ "$gen103" == *DPAD_LEFT* ]]; then
        echo "Stock Y1 (wheel 21/22, media skip 88/87)"
    elif [[ "$gen103" == *MEDIA_PREVIOUS* ]]; then
        echo "Rockbox sideload swap (wheel 88/87, skip 21/22)"
    elif [[ "$gen103" == *DPAD_UP* ]]; then
        echo "Rockbox classic / canonical (wheel 19/20, skip 21/22)"
    else
        echo "Unknown — check keylayout files manually"
    fi
}

print_keycodedisp_table() {
    echo ""
    echo "-- Expected KeyCodeDisp (rockbox-y1 Rockbox.kl on Generic) --"
    echo "| Control      | Keycode name       | Code |"
    echo "| Wheel CCW    | MEDIA_PLAY         | 126  | (mtk 103)"
    echo "| Wheel CW     | MEDIA_PAUSE        | 127  | (mtk 108)"
    echo "| Prev button  | MEDIA_PREVIOUS     | 88   |"
    echo "| Next button  | MEDIA_NEXT         | 87   |"
    echo "| Bottom       | MEDIA_PLAY_PAUSE   | 85   |"
    echo "| Top          | BACK               | 4    |"
    echo "| Center       | ENTER              | 66   |"
    echo ""
    echo "If wheel shows DPAD_LEFT/RIGHT (21/22), Generic.kl is still stock — run push-rockbox-keylayout-adb.sh"
}

echo "== Y1 input verification =="
if ! adb get-state >/dev/null 2>&1; then
    echo "No adb device — connect Y1 to dump live keylayout"
    echo ""
    echo "Reference layouts in repo:"
    echo "  Stock sideload: solar-rom/scripts/mtk-kpd.kl + Stock.kl"
    echo "  Rockbox ROM:    solar-rom/scripts/Rockbox.kl + mtk-kpd-rockbox.kl"
    grep -E '^key (103|105|106|108|114|115)' "$ROOT/solar-rom/scripts/Rockbox.kl" || true
    print_keycodedisp_table
    exit 0
fi

echo ""
echo "-- mtk-kpd.kl scancodes 103–108 --"
L103="$(adb shell "grep '^key 103' /system/usr/keylayout/mtk-kpd.kl" 2>/dev/null | tr -d '\r' | head -1)"
L105="$(adb shell "grep '^key 105' /system/usr/keylayout/mtk-kpd.kl" 2>/dev/null | tr -d '\r' | head -1)"
adb shell "grep -E '^key (103|105|106|108)' /system/usr/keylayout/mtk-kpd.kl" 2>/dev/null | tr -d '\r' || echo "(mtk-kpd.kl not readable)"

echo ""
echo "-- Generic.kl (rockbox-y1 Rockbox.kl markers) --"
G103="$(adb shell "grep '^key 103' /system/usr/keylayout/Generic.kl" 2>/dev/null | tr -d '\r' | head -1)"
G114="$(adb shell "grep '^key 114' /system/usr/keylayout/Generic.kl" 2>/dev/null | tr -d '\r' | head -1)"
adb shell "grep -E '^key (103|105|106|108|114|115)' /system/usr/keylayout/Generic.kl" 2>/dev/null | tr -d '\r' || echo "(Generic.kl not readable)"

echo ""
echo "-- Keylayout consistency --"
if [[ "$G103" == *DPAD_LEFT* ]]; then
    echo "FAIL: Generic.kl wheel still stock (103 -> DPAD_LEFT) — Solar ROM menus will not scroll"
    EXIT_CODE=1
elif [[ "$G103" == *MEDIA_PLAY* ]]; then
    echo "OK: Generic.kl is Y1 Rockbox layout (103/108 wheel MEDIA_PLAY/PAUSE, 105/106 media skip)"
elif [[ -n "$L103" && -n "$G103" && "$L103" != "$G103" ]]; then
    echo "INFO: mtk-kpd and Generic differ on scancode 103 (expected with Rockbox.kl + mtk-kpd-rockbox)"
fi

echo ""
echo "-- Detected layout (Y1KeyMap rules) --"
classify_layout "$G103" "$L105" "$G114"

print_keycodedisp_table

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

if [[ "${1:-}" == "--getevent" ]]; then
    echo ""
    echo "-- getevent (5s sample — spin wheel / press prev/next) --"
    timeout 5 adb shell getevent -l 2>/dev/null || true
fi

echo ""
if [[ "$EXIT_CODE" -eq 0 ]]; then
    echo "Done. Keylayout looks consistent."
else
    echo "Done with ERRORS — fix keylayout before expecting Solar GUI input to work."
fi
exit "$EXIT_CODE"
