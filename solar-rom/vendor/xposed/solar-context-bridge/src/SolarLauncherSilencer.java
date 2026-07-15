package com.solar.launcher.xposed.bridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

import de.robv.android.xposed.XposedHelpers;

/**
 * 2026-07-06 — Safety net when stock HOME resolver still appears (helper should own PM preferred).
 * Layman: if Android tries to show the broken Home picker, jump straight to the app you picked.
 * Technical: launches effective target from persist prop — same routing as LauncherHomeActivity.
 * Reversal: delete hook; rely on helper-only HOME path.
 */
final class SolarLauncherSilencer {

    /** Must match {@link com.solar.launcher.LauncherPreference#PROP_HOME_TARGET}. */
    static final String PROP_HOME_TARGET = "persist.solar.home.target";
    static final String PROP_HOME_COMPONENT = "persist.solar.home.component";
    static final String TARGET_SOLAR = "solar";
    static final String TARGET_ROCKBOX = "rockbox";
    static final String TARGET_JJ = "jj";
    /** 2026-07-08 — Factory Innioasis HOME; was missing and resolver silently opened Solar. */
    static final String TARGET_STOCK = "stock";

    private static final String SOLAR_PKG = "com.solar.launcher";
    private static final String SOLAR_ACTIVITY = "com.solar.launcher.MainActivity";
    private static final String ROCKBOX_PKG = "org.rockbox";
    private static final String ROCKBOX_ACTIVITY = "org.rockbox.RockboxActivity";
    private static final String JJ_PKG = "com.themoon.y1";
    private static final String JJ_ACTIVITY = "com.themoon.y1.MainActivity";
    private static final String INNIOASIS_Y1_PKG = "com.innioasis.y1";
    private static final String INNIOASIS_Y1_ACTIVITY = "com.innioasis.y1.MainActivity";
    private static final String INNIOASIS_Y2_PKG = "com.innioasis.y2";
    private static final String INNIOASIS_Y2_ACTIVITY = "com.innioasis.y2.MainActivity";

    private SolarLauncherSilencer() {}

    /**
     * Read saved HOME choice and start that launcher directly — never fire CATEGORY_HOME
     * (that would reopen the picker we just closed).
     * 2026-07-06 — startActivity before resolver finish; shell am start raced HOME re-resolve.
     */
    static boolean launchSavedHome(Context ctx) {
        if (ctx == null) return false;
        String target = readHomeTarget();
        if (TARGET_ROCKBOX.equals(target) && isPackageDisabled(ROCKBOX_PKG)) {
            target = TARGET_SOLAR;
        }
        if (TARGET_JJ.equals(target) && isPackageDisabled(JJ_PKG)) {
            target = TARGET_SOLAR;
        }
        // 2026-07-08 — Stock missing/disabled → Solar fail-open (was: no stock branch at all).
        if (TARGET_STOCK.equals(target) && resolveStockComponent() == null) {
            target = TARGET_SOLAR;
        }
        Intent launch = buildExplicitHomeIntent(target);
        if (launch == null) return false;
        // #region agent log
        try {
            JSONObject d = new JSONObject();
            d.put("target", target);
            d.put("component", launch.getComponent() != null
                    ? launch.getComponent().flattenToShortString() : "?");
            d.put("via", "startActivity");
            Debug5d4216Log.event("SolarLauncherSilencer.launchSavedHome",
                    "launch explicit HOME", "A", d);
        } catch (Throwable ignored) {}
        // #endregion
        try {
            ctx.startActivity(launch);
            SolarContextBridge.log("silent HOME launch target=" + target);
            return true;
        } catch (Throwable t) {
            // #region agent log
            try {
                JSONObject d = new JSONObject();
                d.put("target", target);
                d.put("err", t.getClass().getSimpleName());
                Debug5d4216Log.event("SolarLauncherSilencer.launchSavedHome",
                        "startActivity failed — shell fallback", "B", d);
            } catch (Throwable ignored) {}
            // #endregion
            return launchSavedHomeShell(target);
        }
    }

    /**
     * Explicit MAIN launch — never CATEGORY_HOME (reopens ResolverActivity).
     * 2026-07-08 — Stock Innioasis Y1/Y2; previously only solar/rockbox/jj.
     * Reversal: drop TARGET_STOCK branch (stock fell through to Solar).
     */
    static Intent buildExplicitHomeIntent(String target) {
        ComponentName component;
        if (TARGET_ROCKBOX.equals(target)) {
            component = new ComponentName(ROCKBOX_PKG, ROCKBOX_ACTIVITY);
        } else if (TARGET_JJ.equals(target)) {
            component = new ComponentName(JJ_PKG, JJ_ACTIVITY);
        } else if (TARGET_STOCK.equals(target)) {
            component = resolveStockComponent();
            if (component == null) {
                component = new ComponentName(SOLAR_PKG, SOLAR_ACTIVITY);
            }
        } else {
            component = new ComponentName(SOLAR_PKG, SOLAR_ACTIVITY);
        }
        Intent launch = new Intent(Intent.ACTION_MAIN);
        launch.setComponent(component);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return launch;
    }

    /** Last resort when Activity.startActivity is blocked from system:ui. */
    private static boolean launchSavedHomeShell(String target) {
        String component = SOLAR_PKG + "/" + SOLAR_ACTIVITY;
        if (TARGET_ROCKBOX.equals(target)) {
            component = ROCKBOX_PKG + "/" + ROCKBOX_ACTIVITY;
        } else if (TARGET_JJ.equals(target)) {
            component = JJ_PKG + "/" + JJ_ACTIVITY;
        } else if (TARGET_STOCK.equals(target)) {
            // 2026-07-08 — Prefer enabled family package for am start -n.
            ComponentName stock = resolveStockComponent();
            if (stock != null) {
                component = stock.getPackageName() + "/" + stock.getClassName();
            }
        }
        try {
            Runtime.getRuntime().exec(new String[]{
                    "sh", "-c",
                    "am start -n " + component + " -f 0x34000000 2>/dev/null"
            });
            SolarContextBridge.log("silent HOME shell target=" + target);
            return true;
        } catch (Throwable t) {
            SolarContextBridge.log("silent HOME launch failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 2026-07-08 — Pick which factory Innioasis activity to open (Y1 or Y2, or PROP override).
     * Layman: open the stock home that is actually installed and enabled.
     * Technical: PROP_HOME_COMPONENT → Y1 → Y2; null when both disabled/missing.
     * Reversal: hardcode Y1 only.
     */
    static ComponentName resolveStockComponent() {
        String flat = readSystemProperty(PROP_HOME_COMPONENT, "");
        if (flat != null && flat.length() > 0 && flat.indexOf('/') > 0) {
            int slash = flat.indexOf('/');
            String pkg = flat.substring(0, slash);
            String cls = flat.substring(slash + 1);
            if ((INNIOASIS_Y1_PKG.equals(pkg) || INNIOASIS_Y2_PKG.equals(pkg))
                    && !isPackageDisabled(pkg)) {
                return new ComponentName(pkg, cls);
            }
        }
        if (!isPackageDisabled(INNIOASIS_Y1_PKG) && isPackagePresent(INNIOASIS_Y1_PKG)) {
            return new ComponentName(INNIOASIS_Y1_PKG, INNIOASIS_Y1_ACTIVITY);
        }
        if (!isPackageDisabled(INNIOASIS_Y2_PKG) && isPackagePresent(INNIOASIS_Y2_PKG)) {
            return new ComponentName(INNIOASIS_Y2_PKG, INNIOASIS_Y2_ACTIVITY);
        }
        return null;
    }

    /** Read persist prop — boot script and Solar Settings keep this in sync. */
    static String readHomeTarget() {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", null);
            Object v = XposedHelpers.callStaticMethod(sp, "get", PROP_HOME_TARGET, TARGET_SOLAR);
            return normalizeHomeTargetForTest(String.valueOf(v));
        } catch (Throwable ignored) {}
        return TARGET_SOLAR;
    }

    /** Test hook — prop string to launcher target without device. */
    static String normalizeHomeTargetForTest(String propValue) {
        if (TARGET_ROCKBOX.equals(propValue)) return TARGET_ROCKBOX;
        if (TARGET_JJ.equals(propValue)) return TARGET_JJ;
        // 2026-07-08 — Stock was coerced to solar here and undid factory-HOME picks.
        if (TARGET_STOCK.equals(propValue)) return TARGET_STOCK;
        return TARGET_SOLAR;
    }

    private static String readSystemProperty(String key, String def) {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", null);
            Object v = XposedHelpers.callStaticMethod(sp, "get", key, def);
            return v != null ? String.valueOf(v) : def;
        } catch (Throwable ignored) {
            return def;
        }
    }

    /** True when pm has disabled the package — do not relaunch during solar HOME. */
    private static boolean isPackageDisabled(String pkg) {
        try {
            Process p = Runtime.getRuntime().exec(new String[] {
                    "sh", "-c", "pm list packages -d " + pkg + " 2>/dev/null"
            });
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), "UTF-8"));
            String line = r.readLine();
            r.close();
            p.waitFor();
            return line != null && line.contains(pkg);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** True when package appears in pm list (enabled or not). */
    private static boolean isPackagePresent(String pkg) {
        try {
            Process p = Runtime.getRuntime().exec(new String[] {
                    "sh", "-c", "pm path " + pkg + " 2>/dev/null"
            });
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), "UTF-8"));
            String line = r.readLine();
            r.close();
            p.waitFor();
            return line != null && line.length() > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
