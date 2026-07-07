#!/system/bin/sh
# 2026-07-06 — Switch effective HOME (Solar/Rockbox/JJ); delegates to solar-launcher-exec.sh.
# Rockbox System menu calls this script with no args → return to Solar.

EXEC="/system/etc/solar/solar-launcher-exec.sh"
if [ ! -f "$EXEC" ]; then
    EXEC="/data/data/solar-launcher-exec.sh"
fi
if [ ! -f "$EXEC" ]; then
    EXEC="/data/data/com.solar.launcher/solar-launcher-exec.sh"
fi

case "$1" in
    --rockbox|-rockbox)
        TARGET="rockbox"
        ;;
    --jj|-jj)
        TARGET="jj"
        ;;
    *)
        TARGET="solar"
        ;;
esac

if [ -f "$EXEC" ]; then
    sh "$EXEC" switch "$TARGET"
    exit $?
fi

# Legacy inline path when exec script not yet staged.
SOLAR_PKG="com.solar.launcher"
ROCKBOX_PKG="org.rockbox"
JJ_PKG="com.themoon.y1"
HELPER_PKG="com.solar.launcher.homehelper"
SOLAR_ACTIVITY="com.solar.launcher/.MainActivity"
ROCKBOX_ACTIVITY="org.rockbox/.RockboxActivity"
JJ_ACTIVITY="com.themoon.y1/.MainActivity"
SET_HOME_ACTION="com.solar.launcher.action.SET_PREFERRED_HOME"
HOME_RECEIVER="com.solar.launcher/.LauncherHomeReceiver"
PROP_HOME_TARGET="persist.solar.home.target"

set_preferred_home() {
    local mode="$1"
    pm enable "$SOLAR_PKG" 2>/dev/null
    pm enable "$HELPER_PKG" 2>/dev/null
    case "$mode" in
        rockbox)
            pm enable "$ROCKBOX_PKG" 2>/dev/null
            pm disable "$JJ_PKG" 2>/dev/null
            setprop "$PROP_HOME_TARGET" rockbox
            ;;
        jj)
            pm enable "$JJ_PKG" 2>/dev/null
            pm disable "$ROCKBOX_PKG" 2>/dev/null
            setprop "$PROP_HOME_TARGET" jj
            ;;
        *)
            mode=solar
            pm disable "$ROCKBOX_PKG" 2>/dev/null
            pm disable "$JJ_PKG" 2>/dev/null
            setprop "$PROP_HOME_TARGET" solar
            ;;
    esac
    am broadcast -a "$SET_HOME_ACTION" -n "$HOME_RECEIVER" --es target "$mode"
    sleep 1
}

case "$TARGET" in
    rockbox)
        set_preferred_home rockbox
        am start -n "$ROCKBOX_ACTIVITY"
        ;;
    jj)
        set_preferred_home jj
        am start -n "$JJ_ACTIVITY"
        ;;
    *)
        set_preferred_home solar
        am force-stop "$ROCKBOX_PKG" 2>/dev/null
        am force-stop "$JJ_PKG" 2>/dev/null
        am start -n "$SOLAR_ACTIVITY"
        ;;
esac
