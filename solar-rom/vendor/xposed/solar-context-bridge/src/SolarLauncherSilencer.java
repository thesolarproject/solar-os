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
    static final String TARGET_SOLAR = "solar";
    static final String TARGET_ROCKBOX = "rockbox";
    static final String TARGET_JJ = "jj";

    private static final String SOLAR_PKG = "com.solar.launcher";
    private static final String SOLAR_ACTIVITY = "com.solar.launcher.MainActivity";
    private static final String ROCKBOX_PKG = "org.rockbox";
    private static final String ROCKBOX_ACTIVITY = "org.rockbox.RockboxActivity";
    private static final String JJ_PKG = "com.themoon.y1";
    private static final String JJ_ACTIVITY = "com.themoon.y1.MainActivity";

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

    /** Explicit MAIN launch — never CATEGORY_HOME (reopens ResolverActivity). */
    static Intent buildExplicitHomeIntent(String target) {
        ComponentName component;
        if (TARGET_ROCKBOX.equals(target)) {
            component = new ComponentName(ROCKBOX_PKG, ROCKBOX_ACTIVITY);
        } else if (TARGET_JJ.equals(target)) {
            component = new ComponentName(JJ_PKG, JJ_ACTIVITY);
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

    /** Read persist prop — boot script and Solar Settings keep this in sync. */
    static String readHomeTarget() {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", null);
            Object v = XposedHelpers.callStaticMethod(sp, "get", PROP_HOME_TARGET, TARGET_SOLAR);
            if (TARGET_ROCKBOX.equals(String.valueOf(v))) {
                return TARGET_ROCKBOX;
            }
            if (TARGET_JJ.equals(String.valueOf(v))) {
                return TARGET_JJ;
            }
        } catch (Throwable ignored) {}
        return TARGET_SOLAR;
    }

    /** Test hook — prop string to launcher target without device. */
    static String normalizeHomeTargetForTest(String propValue) {
        if (TARGET_ROCKBOX.equals(propValue)) return TARGET_ROCKBOX;
        if (TARGET_JJ.equals(propValue)) return TARGET_JJ;
        return TARGET_SOLAR;
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
}
