package com.solar.launcher;

/**
 * 2026-07-06 — Apps menu: show launchable third-party apps including Rockbox/JJ launchers.
 * Layman: Apps lists normal Android apps — launcher picks commit HOME via helper broadcast.
 * Technical: blocklist admin only; {@link AppLauncher} routes HOME packages to helper.
 * Reversal: restore Rockbox in HIDDEN_PACKAGES if Apps menu must stay minimal again.
 */
public final class AppsMenuPolicy {

    /** Hidden from Apps — Xposed admin and overlay companions only. */
    private static final String[] HIDDEN_PACKAGES = {
            "de.robv.android.xposed.installer",
            "com.solar.launcher",
            "com.solar.launcher.globalcontext",
            "com.solar.launcher.homehelper",
    };

    private AppsMenuPolicy() {}

    /** True when the package may show in Apps — all launchable except blocklist. */
    public static boolean isVisibleInAppsMenu(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        for (String hidden : HIDDEN_PACKAGES) {
            if (hidden.equals(packageName)) return false;
        }
        return true;
    }
}
