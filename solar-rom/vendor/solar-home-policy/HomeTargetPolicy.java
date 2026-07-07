package com.solar.home.policy;

/**
 * 2026-07-06 — Shared HOME target constants for helper, Solar client, and Xposed bridge.
 * Layman: one spelling for which app is your home screen (Solar, Rockbox, JJ, or custom).
 * Technical: pure Java policy JAR; no android imports — compiled into helper, Solar, bridge.
 * Reversal: delete JAR; restore duplicated strings in LauncherDefault + shell scripts.
 */
public final class HomeTargetPolicy {

    public static final String PROP_HOME_TARGET = "persist.solar.home.target";
    public static final String PROP_HOME_COMPONENT = "persist.solar.home.component";
    public static final String PROP_HOME_APPLYING = "persist.solar.home.applying";
    /** 2026-07-06 — Uptime-ms deadline while switch/restart suppresses crash/ANR dialogs. */
    public static final String PROP_LAUNCHER_TRANSITION_UNTIL = "sys.solar.launcher.trans_until";

    /** Helper broadcast — commit HOME target (Settings, Apps, overlay). */
    public static final String ACTION_APPLY_HOME_TARGET =
            "com.solar.launcher.homehelper.action.APPLY_HOME_TARGET";
    /** Helper broadcast — force-stop + cold start active launcher only. */
    public static final String ACTION_RESTART_ACTIVE_LAUNCHER =
            "com.solar.launcher.homehelper.action.RESTART_ACTIVE_LAUNCHER";
    public static final String EXTRA_HOME_SOURCE = "source";

    public static final String TARGET_SOLAR = "solar";
    public static final String TARGET_ROCKBOX = "rockbox";
    public static final String TARGET_JJ = "jj";
    public static final String TARGET_CUSTOM = "custom";

    public static final String HELPER_PKG = "com.solar.launcher.homehelper";
    public static final String HELPER_HOME_ACTIVITY = "com.solar.launcher.homehelper.LauncherHomeActivity";
    public static final String ACTION_SET_HOME_TARGET = "com.solar.launcher.homehelper.action.SET_HOME_TARGET";
    /** 2026-07-06 — BACK/POWER hold opens HOME picker when Solar overlay is unavailable. */
    public static final String ACTION_SHOW_LAUNCHER_PICKER =
            "com.solar.launcher.homehelper.action.SHOW_LAUNCHER_PICKER";
    public static final String EXTRA_HOME_TARGET = "target";
    public static final String EXTRA_HOME_COMPONENT = "component";

    public static final String SOLAR_PKG = "com.solar.launcher";
    public static final String SOLAR_ACTIVITY = "com.solar.launcher.MainActivity";
    public static final String ROCKBOX_PKG = "org.rockbox";
    public static final String ROCKBOX_ACTIVITY = "org.rockbox.RockboxActivity";
    public static final String JJ_PKG = "com.themoon.y1";
    public static final String JJ_ACTIVITY = "com.themoon.y1.MainActivity";

    public static final String COMPANION_PKG = "com.solar.launcher.globalcontext";
    /** 2026-07-07 — Cached HOME launcher packages (comma-separated) — shell disable loop reads this. */
    public static final String PROP_HOME_LAUNCHER_PKGS = "persist.solar.home.launch_pkgs";
    public static final String COMPANION_OVERLAY_KEEPALIVE =
            "com.solar.launcher.globalcontext.action.OVERLAY_KEEPALIVE";

    private HomeTargetPolicy() {}

    /** Coerce unknown target strings to solar, rockbox, jj, or custom. */
    public static String normalizeTarget(String target) {
        if (TARGET_ROCKBOX.equals(target)) return TARGET_ROCKBOX;
        if (TARGET_JJ.equals(target)) return TARGET_JJ;
        if (TARGET_CUSTOM.equals(target)) return TARGET_CUSTOM;
        return TARGET_SOLAR;
    }

    /** Flatten ComponentName as pkg/activity for persist prop storage. */
    public static String flattenComponent(String pkg, String activityClass) {
        if (pkg == null || activityClass == null) return "";
        if (activityClass.contains("/")) return activityClass;
        return pkg + "/" + activityClass;
    }

    /** Parse pkg/activity or pkg.activity from prop string — returns null when invalid. */
    public static String[] parseComponent(String flat) {
        if (flat == null || flat.length() == 0) return null;
        String s = flat.trim();
        int slash = s.indexOf('/');
        if (slash > 0 && slash < s.length() - 1) {
            return new String[] { s.substring(0, slash), s.substring(slash + 1) };
        }
        return null;
    }

    /** True when target needs alternate launcher package enabled (not solar/custom-only). */
    public static boolean isAlternateHomeTarget(String target) {
        return TARGET_ROCKBOX.equals(target) || TARGET_JJ.equals(target);
    }

    /**
     * 2026-07-06 — Resolve pkg/activity for the user's effective HOME (not the helper).
     * Layman: which real app to open after the middle-man HOME button fires.
     * Technical: custom uses persist component; unknown → Solar MainActivity.
     */
    public static String[] resolveLaunchComponent(String target, String customComponentFlat) {
        String normalized = normalizeTarget(target);
        if (TARGET_CUSTOM.equals(normalized)) {
            String[] parsed = parseComponent(customComponentFlat);
            if (parsed != null) return parsed;
            return new String[] { SOLAR_PKG, SOLAR_ACTIVITY };
        }
        if (TARGET_ROCKBOX.equals(normalized)) {
            return new String[] { ROCKBOX_PKG, ROCKBOX_ACTIVITY };
        }
        if (TARGET_JJ.equals(normalized)) {
            return new String[] { JJ_PKG, JJ_ACTIVITY };
        }
        return new String[] { SOLAR_PKG, SOLAR_ACTIVITY };
    }

    /** Flatten {@link #resolveLaunchComponent} as pkg/activity for shell am start -n. */
    public static String flattenLaunchComponent(String target, String customComponentFlat) {
        String[] parts = resolveLaunchComponent(target, customComponentFlat);
        return flattenComponent(parts[0], parts[1]);
    }
}
