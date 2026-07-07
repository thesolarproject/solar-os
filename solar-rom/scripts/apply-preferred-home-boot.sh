#!/system/bin/sh
# 2026-07-06 — Every boot: re-apply saved effective HOME; PM preferred always Solar Home Helper.
# Reads persist.solar.home.target (solar, rockbox, or jj), broadcasts to Solar for PM pin,
# and keeps Solar's overlay service warm so the global quick menu works over alternate HOME apps.

SOLAR_PKG="com.solar.launcher"
ROCKBOX_PKG="org.rockbox"
JJ_PKG="com.themoon.y1"
HELPER_PKG="com.solar.launcher.homehelper"
PROP_HOME_TARGET="persist.solar.home.target"
SET_HOME_ACTION="com.solar.launcher.action.SET_PREFERRED_HOME"
HOME_RECEIVER="com.solar.launcher/.LauncherHomeReceiver"
OVERLAY_KEEPALIVE="com.solar.launcher.action.OVERLAY_KEEPALIVE"
OVERLAY_SERVICE="com.solar.launcher/.SolarOverlayService"
WATCHDOG_SERVICE="com.solar.launcher/.LauncherWatchdogService"
FIRST_BOOT_MARKER="/data/data/.solar_rom_home_ready"

ensure_helper_registered() {
    if [ -f /system/app/SolarHomeHelper.apk ] && ! pm path "$HELPER_PKG" >/dev/null 2>&1; then
        if ! pm install -r /system/app/SolarHomeHelper.apk >/dev/null 2>&1; then
            log -p e -t SolarHome "pm install SolarHomeHelper failed — run platform repair"
        fi
    fi
}

ensure_rockbox_registered() {
    if [ -f /system/app/org.rockbox.apk ] && ! pm path "$ROCKBOX_PKG" >/dev/null 2>&1; then
        if ! pm install -r /system/app/org.rockbox.apk >/dev/null 2>&1; then
            log -p e -t SolarHome "pm install org.rockbox failed — reflash Solar Y2 ROM"
        fi
    fi
}

ensure_jj_registered() {
    if [ -f /system/app/com.themoon.y1.apk ] && ! pm path "$JJ_PKG" >/dev/null 2>&1; then
        if ! pm install -r /system/app/com.themoon.y1.apk >/dev/null 2>&1; then
            log -p e -t SolarHome "pm install JJ failed — install from Settings when online"
        fi
    fi
}

ensure_notpipe_registered() {
    NOTPIPE_PKG="io.github.gohoski.notpipe"
    if [ -f /system/app/io.github.gohoski.notpipe.apk ] && ! pm path "$NOTPIPE_PKG" >/dev/null 2>&1; then
        if ! pm install -r /system/app/io.github.gohoski.notpipe.apk >/dev/null 2>&1; then
            log -p e -t SolarHome "pm install notPipe failed — run platform repair in Settings"
        fi
    fi
}

read_home_target() {
    TARGET="$(getprop "$PROP_HOME_TARGET")"
    case "$TARGET" in
        rockbox) echo rockbox ;;
        jj) echo jj ;;
        *) echo solar ;;
    esac
}

apply_home_target() {
    TARGET="$1"
    pm enable "$SOLAR_PKG" 2>/dev/null
    pm enable "$HELPER_PKG" 2>/dev/null
    case "$TARGET" in
        rockbox)
            if pm path "$ROCKBOX_PKG" >/dev/null 2>&1; then
                pm enable "$ROCKBOX_PKG" 2>/dev/null
                pm disable "$JJ_PKG" 2>/dev/null
                setprop "$PROP_HOME_TARGET" rockbox
            else
                TARGET=solar
                pm disable "$ROCKBOX_PKG" 2>/dev/null
                pm disable "$JJ_PKG" 2>/dev/null
                setprop "$PROP_HOME_TARGET" solar
            fi
            ;;
        jj)
            if pm path "$JJ_PKG" >/dev/null 2>&1; then
                pm enable "$JJ_PKG" 2>/dev/null
                pm disable "$ROCKBOX_PKG" 2>/dev/null
                setprop "$PROP_HOME_TARGET" jj
            else
                TARGET=solar
                pm disable "$ROCKBOX_PKG" 2>/dev/null
                pm disable "$JJ_PKG" 2>/dev/null
                setprop "$PROP_HOME_TARGET" solar
            fi
            ;;
        *)
            TARGET=solar
            pm disable "$ROCKBOX_PKG" 2>/dev/null
            pm disable "$JJ_PKG" 2>/dev/null
            setprop "$PROP_HOME_TARGET" solar
            ;;
    esac
    am broadcast -a "$SET_HOME_ACTION" -n "$HOME_RECEIVER" --es target "$TARGET"
    sleep 1
}

ensure_overlay_host() {
    am startservice -a "$OVERLAY_KEEPALIVE" -n "$OVERLAY_SERVICE" 2>/dev/null
    am startservice -n com.solar.launcher.homehelper/.LauncherEnforcerService 2>/dev/null
    am startservice -n com.solar.launcher.globalcontext/.GlobalInputCoordinatorService 2>/dev/null
    if [ -f /system/etc/solar/solar-platform-daemon.sh ]; then
        sh /system/etc/solar/solar-platform-daemon.sh &
    fi
}

ensure_rockbox_registered
ensure_jj_registered
ensure_notpipe_registered
ensure_helper_registered

i=0
while [ "$i" -lt 45 ]; do
    pm path "$SOLAR_PKG" >/dev/null 2>&1 && break
    sleep 1
    i=$((i + 1))
done
pm path "$SOLAR_PKG" >/dev/null 2>&1 || exit 0

TARGET="$(read_home_target)"

if [ ! -f "$FIRST_BOOT_MARKER" ]; then
    if [ -z "$(getprop "$PROP_HOME_TARGET")" ]; then
        TARGET=solar
    fi
    touch "$FIRST_BOOT_MARKER"
    log -p i -t SolarHome "first boot HOME target=$TARGET"
fi

apply_home_target "$TARGET"
ensure_overlay_host
log -p i -t SolarHome "boot HOME applied target=$TARGET"
