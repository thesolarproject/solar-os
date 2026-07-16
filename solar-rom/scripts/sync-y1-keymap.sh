#!/system/bin/sh
# 2026-07-06 — Generic/Stock/Rockbox = canonical Y1-Rockbox.kl or Y2-Rockbox.kl; mtk mirrors per family.
# 2026-07-15 — A5: restore A5-mtk.kl / A5.kl (not Y1 wheel maps). Family pin / model A5 / Timmkoo.
# 2026-07-16 — Detect A5 by panel size (240×320) when model props lie as Y1; never treat bare Timmkoo as A5.
# Pick family from display → persist.solar.device_family → model — never from which .kl files exist.
# Reversal: drop display probe + A5 branch; A5 boot would reapply Y1-Rockbox.kl and break face/volume keys.

FAM="$(getprop persist.solar.device_family 2>/dev/null | tr 'A-Z' 'a-z')"
MODEL="$(getprop ro.product.model 2>/dev/null)"
MANU="$(getprop ro.product.manufacturer 2>/dev/null)"

# Panel size: fb0 "W,H" or dumpsys fallback. A5 is ~240×320; Y1 is ~480×360.
DISP_W=0
DISP_H=0
if [ -r /sys/class/graphics/fb0/virtual_size ]; then
    # shellcheck disable=SC2039
    VS="$(cat /sys/class/graphics/fb0/virtual_size 2>/dev/null | tr ',' ' ')"
    # virtual_size is "width,height"
    set -- $VS
    DISP_W="${1:-0}"
    DISP_H="${2:-0}"
fi
if [ "$DISP_W" = "0" ] || [ -z "$DISP_W" ]; then
    # Fallback: dumpsys window init=WxH (slower; boot path may skip if busy).
    WIN="$(dumpsys window 2>/dev/null | tr -d '\r' | grep -o 'init=[0-9]*x[0-9]*' | head -1)"
    WIN="${WIN#init=}"
    DISP_W="${WIN%x*}"
    DISP_H="${WIN#*x}"
fi
# Normalize empties
case "$DISP_W" in ''|*[!0-9]*) DISP_W=0 ;; esac
case "$DISP_H" in ''|*[!0-9]*) DISP_H=0 ;; esac

looks_a5_display() {
    w="$1"; h="$2"
    [ "$w" -gt 0 ] 2>/dev/null || return 1
    [ "$h" -gt 0 ] 2>/dev/null || return 1
    # min/max for order independence
    if [ "$w" -le "$h" ]; then a=$w; b=$h; else a=$h; b=$w; fi
    # 240×320 ±20
    [ "$a" -ge 220 ] && [ "$a" -le 260 ] && [ "$b" -ge 300 ] && [ "$b" -le 340 ]
}

looks_y1_display() {
    w="$1"; h="$2"
    [ "$w" -gt 0 ] 2>/dev/null || return 1
    [ "$h" -gt 0 ] 2>/dev/null || return 1
    if [ "$w" -le "$h" ]; then a=$w; b=$h; else a=$h; b=$w; fi
    # 360×480 ±20
    [ "$a" -ge 340 ] && [ "$a" -le 380 ] && [ "$b" -ge 460 ] && [ "$b" -le 500 ]
}

IS_Y2=0
IS_A5=0
# Display first — stock A5 ROM claims model=Y1.
if looks_a5_display "$DISP_W" "$DISP_H"; then
    IS_A5=1
elif looks_y1_display "$DISP_W" "$DISP_H"; then
    IS_A5=0
    # real Y1 panel — keep Y1 maps even if a stale a5 pin remains
    case "$FAM" in a5) FAM=y1 ;; esac
fi
if [ "$IS_A5" = "0" ]; then
    case "$FAM" in
        y2) IS_Y2=1 ;;
        a5) IS_A5=1 ;;
    esac
fi
if [ "$IS_Y2" = "0" ] && [ "$IS_A5" = "0" ]; then
    case "$MODEL" in
        *[Yy]2*) IS_Y2=1 ;;
        *[Aa]5*) IS_A5=1 ;;
    esac
fi
# 2026-07-16 — Do NOT treat bare manufacturer Timmkoo as A5 (Y1 is also Timmkoo).
# Was: *[Tt]immkoo*) IS_A5=1 — reclassified every Y1 as A5 when A5-mtk.kl present.

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
