#!/system/bin/sh
# 2026-07-06 — Generic/Stock/Rockbox = canonical Y1-Rockbox.kl or Y2-Rockbox.kl; mtk mirrors per family.
# 2026-07-15 — A5: restore A5-mtk.kl / A5.kl (not Y1 wheel maps). Family pin / model A5 / Timmkoo.
# Pick family from persist.solar.device_family then ro.product.model — never from which .kl files exist.
# Reversal: drop A5 branch; A5 boot would reapply Y1-Rockbox.kl and break face/volume keys.

FAM="$(getprop persist.solar.device_family 2>/dev/null | tr 'A-Z' 'a-z')"
MODEL="$(getprop ro.product.model 2>/dev/null)"
MANU="$(getprop ro.product.manufacturer 2>/dev/null)"

IS_Y2=0
IS_A5=0
case "$FAM" in
    y2) IS_Y2=1 ;;
    a5) IS_A5=1 ;;
esac
if [ "$IS_Y2" = "0" ] && [ "$IS_A5" = "0" ]; then
    case "$MODEL" in
        *[Yy]2*) IS_Y2=1 ;;
        *[Aa]5*) IS_A5=1 ;;
    esac
fi
if [ "$IS_Y2" = "0" ] && [ "$IS_A5" = "0" ]; then
    case "$MANU" in
        *[Tt]immkoo*) IS_A5=1 ;;
    esac
fi

mount -o remount,rw /system 2>/dev/null || true

# 2026-07-15 — A5 face DPAD + volume + MEDIA_STOP power (same playbook as push-a5-keymap.sh).
if [ "$IS_A5" = "1" ]; then
    MTK=/system/etc/solar/A5-mtk.kl
    GEN=/system/etc/solar/A5.kl
    [ -f "$MTK" ] || exit 0
    [ -f "$GEN" ] || exit 0
    setprop persist.solar.device_family a5 2>/dev/null
    cp "$MTK" /system/usr/keylayout/mtk-kpd.kl
    cp "$MTK" /system/etc/solar/A5-mtk.kl
    chmod 644 /system/usr/keylayout/mtk-kpd.kl
    if [ -e /system/usr/keylayout/mtk-tpd-kpd.kl ]; then
        cp "$MTK" /system/usr/keylayout/mtk-tpd-kpd.kl
        chmod 644 /system/usr/keylayout/mtk-tpd-kpd.kl
    fi
    for f in Generic.kl Stock.kl Rockbox.kl A5.kl; do
        cp "$GEN" "/system/usr/keylayout/$f"
        chmod 644 "/system/usr/keylayout/$f"
    done
    # Drop Y1/Y2 Rockbox canon names so wheel sync cannot silently take over.
    rm -f /system/usr/keylayout/Y1-Rockbox.kl /system/usr/keylayout/Y2-Rockbox.kl 2>/dev/null
    exit 0
fi

if [ "$IS_Y2" = "1" ]; then
    SRC=/system/etc/solar/Y2-Rockbox.kl
else
    SRC=/system/etc/solar/Y1-Rockbox.kl
    rm -f /system/etc/solar/Y2-Rockbox.kl 2>/dev/null
fi
[ -f "$SRC" ] || exit 0
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
