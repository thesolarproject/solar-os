#!/system/bin/sh
# MTK init.rc: service flash_recovery /system/etc/install-recovery.sh
# Runs before zygote — apply staged Xposed app_process while nothing holds the binary open.
if [ -f /system/bin/app_process.xposed.staged ]; then
    cp -f /system/bin/app_process.xposed.staged /system/bin/app_process
    chmod 755 /system/bin/app_process
    chown root:shell /system/bin/app_process 2>/dev/null || chown root.root /system/bin/app_process
    rm -f /system/bin/app_process.xposed.staged
fi
# Starts daemonsu early (SELinux enforcing Y2) then Solar boot hooks in -2.sh.
/system/xbin/daemonsu --auto-daemon &
[ -f /system/etc/install-recovery-2.sh ] && sh /system/etc/install-recovery-2.sh &
