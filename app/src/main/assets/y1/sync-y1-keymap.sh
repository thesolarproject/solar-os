#!/system/bin/sh
# 2026-07-06 — Generic/Stock/Rockbox = canonical Y1-Rockbox.kl or Y2-Rockbox.kl; mtk mirrors per family.
# Pick family from ro.product.model — never from which .kl files exist (stale Y2 on Y1 broke wheel).
MODEL="$(getprop ro.product.model 2>/dev/null)"
case "$MODEL" in
    *[Yy]2*)
        IS_Y2=1
        SRC=/system/etc/solar/Y2-Rockbox.kl
        ;;
    *)
        IS_Y2=0
        SRC=/system/etc/solar/Y1-Rockbox.kl
        rm -f /system/etc/solar/Y2-Rockbox.kl 2>/dev/null
        ;;
esac
[ -f "$SRC" ] || exit 0
mount -o remount,rw /system 2>/dev/null || true
for f in Generic.kl Stock.kl Rockbox.kl Y1-Rockbox.kl Y2-Rockbox.kl; do
    cp "$SRC" "/system/usr/keylayout/$f"
    chmod 644 "/system/usr/keylayout/$f"
done
if [ "$IS_Y2" = "1" ]; then
    cp "$SRC" /system/usr/keylayout/mtk-kpd.kl
    cp "$SRC" /system/usr/keylayout/mtk-tpd-kpd.kl
    chmod 644 /system/usr/keylayout/mtk-kpd.kl /system/usr/keylayout/mtk-tpd-kpd.kl
else
    SED=/system/bin/sed
    [ -x "$SED" ] || SED=sed
    STOCK=/system/etc/solar/mtk-kpd.y1.stock.kl
    KPD=/system/usr/keylayout/mtk-kpd.kl
    KPD_BYTES=0
    if [ -f "$KPD" ]; then
        KPD_BYTES="$(wc -c < "$KPD" | tr -d ' ')"
    fi
    # 2026-07-06 — Empty/truncated mtk-kpd (OTA drift) kills wheel+side; restore stock then patch wheel.
    if [ ! -s "$KPD" ] || [ "$KPD_BYTES" -gt 3000 ]; then
        if [ -f "$STOCK" ]; then
            cp "$STOCK" "$KPD"
        fi
    fi
    if [ -f "$KPD" ] && [ -s "$KPD" ]; then
        "$SED" -i 's/^key 105[[:space:]].*/key 105   MEDIA_PLAY/' "$KPD"
        "$SED" -i 's/^key 106[[:space:]].*/key 106   MEDIA_PAUSE/' "$KPD"
        chmod 644 "$KPD"
        cp "$KPD" /system/usr/keylayout/mtk-tpd-kpd.kl
        chmod 644 /system/usr/keylayout/mtk-tpd-kpd.kl
    fi
fi
