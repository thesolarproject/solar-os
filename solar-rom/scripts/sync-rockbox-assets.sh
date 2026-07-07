#!/system/bin/sh
# Copy staged .rockbox tree into sdcard0 + app_rockbox (Y2 has no unzip on device).
APK=
APK_TAG=
SD_ROCKBOX=/storage/sdcard0/.rockbox
APP_ROCKBOX=/data/data/org.rockbox/app_rockbox/.rockbox
STAGED=/system/etc/solar/rockbox-dot-rockbox
MARKER="$APP_ROCKBOX/.solar_assets_path"

resolve_apk() {
    APK_TAG=$(pm path org.rockbox 2>/dev/null)
    APK=${APK_TAG#package:}
    [ -z "$APK" ] && APK=/system/app/org.rockbox.apk
    [ -z "$APK_TAG" ] && APK_TAG="package:$APK"
    [ -f "$APK" ] || return 1
    return 0
}

if [ -f /system/etc/solar/sync-rockbox-libs.sh ]; then
    sh /system/etc/solar/sync-rockbox-libs.sh
fi

resolve_apk || exit 0

needs_sync=0
if [ ! -f "$APP_ROCKBOX/rocks/viewers/db_folder_select.rock" ]; then
    needs_sync=1
elif [ ! -f "$MARKER" ] || [ "$(cat "$MARKER" 2>/dev/null)" != "$APK_TAG" ]; then
    needs_sync=1
fi

[ "$needs_sync" -eq 1 ] || exit 0

if [ ! -f "$STAGED/rocks/viewers/db_folder_select.rock" ]; then
    log -p w -t SolarRockbox "missing staged .rockbox at $STAGED"
    exit 1
fi

mkdir -p "$SD_ROCKBOX" "$APP_ROCKBOX"
cp -a "$STAGED/." "$SD_ROCKBOX/"
cp -a "$STAGED/." "$APP_ROCKBOX/"

if [ -d /storage/sdcard1 ] && [ -d /storage/sdcard0 ] \
        && [ -f /system/etc/solar/rockbox-y2-config.cfg ]; then
    cp /system/etc/solar/rockbox-y2-config.cfg "$SD_ROCKBOX/config.cfg"
    cp /system/etc/solar/rockbox-y2-config.cfg "$APP_ROCKBOX/config.cfg"
fi

chmod -R a+rX "$SD_ROCKBOX" 2>/dev/null
chmod -R a+rX "$APP_ROCKBOX" 2>/dev/null
echo "$APK_TAG" > "$MARKER"
log -p i -t SolarRockbox "synced .rockbox assets from $STAGED"
