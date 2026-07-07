package com.solar.launcher.globalcontext;

import android.content.Context;
import android.content.pm.ApplicationInfo;

/**
 * 2026-07-05 — Detect whether Solar APK is installed/running.
 * Layman: companion checks if the real app can paint the full quick menu.
 * Technical: pm ApplicationInfo probe; companion shell is fallback only when false.
 */
public final class CompanionSolarProbe {

    private static final String SOLAR_PKG = "com.solar.launcher";

    private CompanionSolarProbe() {}

    public static boolean isSolarInstalled(Context ctx) {
        if (ctx == null) return false;
        try {
            ApplicationInfo info = ctx.getPackageManager().getApplicationInfo(SOLAR_PKG, 0);
            return info != null;
        } catch (Exception e) {
            return false;
        }
    }
}
