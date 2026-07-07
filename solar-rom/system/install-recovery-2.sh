#!/system/bin/sh
# Solar additions — kept separate so a future su swap can replace install-recovery.sh safely.
if [ -d /system/etc/init.d ]; then
    for script in /system/etc/init.d/*; do
        [ -f "$script" ] && [ -x "$script" ] || continue
        # install-recovery.sh already started daemonsu — skip 99SuperSUDaemon only.
        case "$script" in
            */99SuperSUDaemon) continue ;;
        esac
        sh "$script" &
    done
fi
