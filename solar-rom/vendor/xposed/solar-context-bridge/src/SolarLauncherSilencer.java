package com.solar.launcher.xposed.bridge;

import android.content.Context;

import de.robv.android.xposed.XposedHelpers;

/**
 * When Android tries to show the unusable HOME picker, open the app the user already chose in Solar.
 */
final class SolarLauncherSilencer {

    /** Must match {@link com.solar.launcher.LauncherPreference#PROP_HOME_TARGET}. */
    static final String PROP_HOME_TARGET = "persist.solar.home.target";
    static final String TARGET_SOLAR = "solar";
    static final String TARGET_ROCKBOX = "rockbox";

    private static final String SOLAR_PKG = "com.solar.launcher";
    private static final String SOLAR_ACTIVITY = "com.solar.launcher/.MainActivity";
    private static final String ROCKBOX_PKG = "org.rockbox";
    private static final String ROCKBOX_ACTIVITY = "org.rockbox/.RockboxActivity";

    private SolarLauncherSilencer() {}

    /**
     * Read saved HOME choice and start that launcher directly — never fire CATEGORY_HOME
     * (that would reopen the picker we just closed).
     */
    static void launchSavedHome(Context ctx) {
        if (ctx == null) return;
        String target = readHomeTarget();
        String component = TARGET_ROCKBOX.equals(target) ? ROCKBOX_ACTIVITY : SOLAR_ACTIVITY;
        try {
            Runtime.getRuntime().exec(new String[]{
                    "sh", "-c",
                    "am start -n " + component
                            + " -f 0x34000000 2>/dev/null"
            });
            SolarContextBridge.log("silent HOME launch target=" + target);
        } catch (Throwable t) {
            SolarContextBridge.log("silent HOME launch failed: " + t.getClass().getSimpleName());
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
        } catch (Throwable ignored) {}
        return TARGET_SOLAR;
    }

    /** Test hook — prop string to launcher target without device. */
    static String normalizeHomeTargetForTest(String propValue) {
        if (TARGET_ROCKBOX.equals(propValue)) return TARGET_ROCKBOX;
        return TARGET_SOLAR;
    }
}
