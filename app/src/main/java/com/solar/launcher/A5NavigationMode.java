package com.solar.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/**
 * 2026-07-11 — A5 prefs: face/side nav; portrait / landscape / auto orientation.
 * Layman: pick which buttons scroll menus; tall, sideways, or follow the sensor.
 * Tech: SharedPreferences; auto default uses Configuration for chrome.
 * Reversal: remove prefs; hardcode side-nav + landscape like Y1/Y2.
 */
public final class A5NavigationMode {

    public static final String PREFS = "SOLAR_SETTINGS";
    /** face = Back/Home/AppSwitch navigate; side = VolUp/VolDown/Power navigate. */
    public static final String PREF_MENU_NAV = "a5_menu_nav_buttons";
    public static final String NAV_FACE = "face";
    public static final String NAV_SIDE = "side";
    /** portrait | landscape | auto (sensor). Default portrait — A5 is tall 240×320. */
    public static final String PREF_ORIENTATION = "a5_orientation";
    public static final String ORIENT_PORTRAIT = "portrait";
    public static final String ORIENT_LANDSCAPE = "landscape";
    public static final String ORIENT_AUTO = "auto";

    private A5NavigationMode() {}

    /** True when face row owns menu focus (side row is media). Default face. */
    public static boolean isFaceNav(Context ctx) {
        return NAV_FACE.equals(menuNav(ctx));
    }

    /** True when side row owns menu focus (face row is media). */
    public static boolean isSideNav(Context ctx) {
        return NAV_SIDE.equals(menuNav(ctx));
    }

    /** Resolved menu-nav mode — face unless user picked side. */
    public static String menuNav(Context ctx) {
        if (ctx == null) return NAV_FACE;
        String v = prefs(ctx).getString(PREF_MENU_NAV, NAV_FACE);
        return NAV_SIDE.equals(v) ? NAV_SIDE : NAV_FACE;
    }

    /** Persist face|side menu navigation. */
    public static void setMenuNav(Context ctx, String mode) {
        if (ctx == null) return;
        if (!NAV_FACE.equals(mode) && !NAV_SIDE.equals(mode)) return;
        prefs(ctx).edit().putString(PREF_MENU_NAV, mode).commit();
    }

    /** Effective portrait chrome — locked portrait, or auto + config portrait. */
    public static boolean isPortrait(Context ctx) {
        return A5PortraitChrome.usePortraitChrome(ctx);
    }

    /**
     * 2026-07-14 — Landscape A5: isPortrait false only when not physically tall.
     * Mirrors chrome so scale is 1f while waiting for rotate off 240×320.
     */
    public static boolean isLandscape(Context ctx) {
        if (!DeviceFeatures.isA5() || ctx == null) return false;
        return !isPortrait(ctx);
    }

    /**
     * 2026-07-14 — Pref value: portrait | landscape | auto.
     * Was: default auto (sensor), which left A5 on landscape manifest until rotate.
     * Now: default portrait for tall 240×320 A5.
     * Reversal: restore ORIENT_AUTO as getString default.
     */
    public static String orientation(Context ctx) {
        if (ctx == null) return ORIENT_PORTRAIT;
        String v = prefs(ctx).getString(PREF_ORIENTATION, ORIENT_PORTRAIT);
        if (ORIENT_PORTRAIT.equals(v) || ORIENT_LANDSCAPE.equals(v) || ORIENT_AUTO.equals(v)) {
            return v;
        }
        return ORIENT_PORTRAIT;
    }

    /** Persist portrait|landscape|auto. */
    public static void setOrientation(Context ctx, String mode) {
        if (ctx == null) return;
        if (!ORIENT_PORTRAIT.equals(mode) && !ORIENT_LANDSCAPE.equals(mode)
                && !ORIENT_AUTO.equals(mode)) {
            return;
        }
        prefs(ctx).edit().putString(PREF_ORIENTATION, mode).commit();
    }

    /** Cycle Settings row: auto → portrait → landscape → auto. */
    public static void cycleOrientation(Context ctx) {
        String cur = orientation(ctx);
        if (ORIENT_AUTO.equals(cur)) setOrientation(ctx, ORIENT_PORTRAIT);
        else if (ORIENT_PORTRAIT.equals(cur)) setOrientation(ctx, ORIENT_LANDSCAPE);
        else setOrientation(ctx, ORIENT_AUTO);
    }

    /**
     * 2026-07-11 / 2026-07-14 — Tall chrome themes: no masks, full-width menus.
     * Layman: upright layout — menus use the whole width, no picture frame.
     * Tech: A5 portrait chrome **or** Y1/Y2 portrait experiment (shared gate).
     * Was: A5-only. Reversal: return DeviceFeatures.isA5() && isPortrait(ctx).
     */
    public static boolean forcePortraitThemeRules(Context ctx) {
        return A5PortraitChrome.usePortraitChrome(ctx);
    }

    /**
     * 2026-07-14 — Landscape theme scale only when sideways chrome is active.
     * Mirrors {@link A5PortraitChrome#usePortraitChrome} so tall-buffer fail-open stays 1f.
     */
    public static float landscapeThemeScale(Context ctx) {
        // 2026-07-14 — Null ctx fail-open to 1f (portrait default); was treated as landscape via
        // usePortraitChrome(null)=false. Reversal: drop ctx==null check.
        if (ctx == null || !DeviceFeatures.isA5() || isPortrait(ctx)) return 1f;
        return 240f / 360f;
    }

    /**
     * 2026-07-14 — Multiply a Y1 layout px by landscape 240p scale (1.0 when not A5 landscape).
     * Layman: shrink normal sideways menu sizes to fit the short A5 panel.
     * Tech: px * landscapeThemeScale; portrait / non-A5 unchanged.
     * Reversal: return px unchanged.
     */
    public static int scaleLayoutPx(Context ctx, int px) {
        return scaleLayoutPx(px, landscapeThemeScale(ctx));
    }

    /** Pure scale helper — unit-testable without SharedPreferences. */
    public static int scaleLayoutPx(int px, float scale) {
        if (scale >= 0.999f) return px;
        if (scale <= 0f) return Math.max(1, px);
        return Math.max(1, Math.round(px * scale));
    }

    /**
     * 2026-07-14 — Y1 NP composition after A5 landscape scale (authoring dp @ mdpi).
     * Layman: miniature Now Playing must still fit art + info side-by-side and transport under.
     * Tech: padLeft+artW+infoML+infoW == landW; status+(artSlot−overshoot)+transport == landH.
     * Authoring atoms match dimens_y1 (5+235+5+235 / 45+267+48). Reversal: delete; keep scaleLayoutPx.
     */
    public static boolean npLandscapeCompositionFits(float scale, int landW, int landH) {
        int pad = scaleLayoutPx(5, scale);
        int art = scaleLayoutPx(235, scale);
        int gap = scaleLayoutPx(5, scale);
        int info = scaleLayoutPx(235, scale);
        int status = scaleLayoutPx(45, scale);
        int slot = scaleLayoutPx(281, scale);
        int over = scaleLayoutPx(14, scale);
        int transport = scaleLayoutPx(48, scale);
        return (pad + art + gap + info) == landW
                && (status + (slot - over) + transport) == landH;
    }

    public static int portraitWidthPx() {
        return 240;
    }

    public static int portraitHeightPx() {
        return 320;
    }

    public static int landscapeWidthPx() {
        return 320;
    }

    public static int landscapeHeightPx() {
        return 240;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
