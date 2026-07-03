#!/system/bin/sh
# ponytail: Generic/Stock/Rockbox = canonical Rockbox keymap (Y2-Rockbox.kl on Y2, else Y1-Rockbox.kl).
# mtk-tpd-kpd must mirror mtk-kpd (163/165→MEDIA_NEXT/PREVIOUS) — not the full PC keyboard map.
SED=/system/bin/sed
[ -x "$SED" ] || SED=sed
SRC=/system/etc/solar/Y2-Rockbox.kl
IS_Y2=1
[ -f "$SRC" ] || { SRC=/system/etc/solar/Y1-Rockbox.kl; IS_Y2=0; }
[ -f "$SRC" ] || exit 0
mount -o remount,rw /system 2>/dev/null || true
for f in Generic.kl Stock.kl Rockbox.kl Y1-Rockbox.kl Y2-Rockbox.kl; do
    cp "$SRC" "/system/usr/keylayout/$f"
    chmod 644 "/system/usr/keylayout/$f"
done
# mtk-kpd keeps GPIO keys; patch wheel scancodes 105/106 (stock maps them to skip keys on Y2).
KPD=/system/usr/keylayout/mtk-kpd.kl
if [ -f "$KPD" ]; then
    "$SED" -i 's/^key 105[[:space:]].*/key 105   MEDIA_PLAY/' "$KPD"
    "$SED" -i 's/^key 106[[:space:]].*/key 106   MEDIA_PAUSE/' "$KPD"
    if [ "$IS_Y2" = "1" ]; then
        "$SED" -i 's/^key 163[[:space:]].*/key 163   MEDIA_NEXT/' "$KPD"
        "$SED" -i 's/^key 165[[:space:]].*/key 165   MEDIA_PREVIOUS/' "$KPD"
    fi
    chmod 644 "$KPD"
    cp "$KPD" /system/usr/keylayout/mtk-tpd-kpd.kl
    chmod 644 /system/usr/keylayout/mtk-tpd-kpd.kl
fi
