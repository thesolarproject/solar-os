#!/system/bin/sh
# 2026-07-06 — Supervises helper enforcer, companion overlay daemons, and rescue evdev tier.
# Layman: keeps hold-to-menu and launcher enforcement alive even when Solar is not foreground.
# Technical: hold timing lives in GlobalInputPolicy + Xposed; this script only babysits processes.
# Reversal: remove from 99SolarInit.sh; apply-preferred-home-boot starts services directly.

RESCUE_DAEMON="/system/etc/solar/solar-rescue-daemon.sh"
LAUNCHER_EXEC="/system/etc/solar/solar-launcher-exec.sh"
HELPER_ENFORCER="com.solar.launcher.homehelper/.LauncherEnforcerService"
COORDINATOR="com.solar.launcher.globalcontext/.GlobalInputCoordinatorService"
PIDFILE="/data/local/tmp/solar-platform-daemon.pid"
TAG="SolarPlatformDaemon"

if [ -f "$PIDFILE" ]; then
    _old=$(cat "$PIDFILE" 2>/dev/null)
    if [ -n "$_old" ] && kill -0 "$_old" 2>/dev/null; then
        exit 0
    fi
fi
echo $$ >"$PIDFILE"

log -p i -t "$TAG" "platform supervisor pid=$$"

start_rescue_daemon() {
    if [ -f "$RESCUE_DAEMON" ]; then
        sh "$RESCUE_DAEMON" &
    fi
}

rescue_running() {
    for _pf in /proc/[0-9]*/cmdline; do
        tr '\0' ' ' < "$_pf" 2>/dev/null | grep -q 'solar-rescue-daemon' && return 0
    done
    return 1
}

start_rescue_daemon

while true; do
    am startservice -n "$HELPER_ENFORCER" 2>/dev/null
    am startservice -n "$COORDINATOR" 2>/dev/null
    if ! rescue_running; then
        start_rescue_daemon
    fi
    sleep 30
done
