package com.solar.ota;

import android.content.Context;
import android.content.pm.PackageInfo;

/** 2026-07-05 — Installed Solar launcher version (PackageManager), not the updater app itself. */
public final class SolarLauncherVersion {
    public static final String SOLAR_PACKAGE = "com.solar.launcher";

    private SolarLauncherVersion() {}

    public static String installedVersionName(Context context) {
        if (context == null) return "";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(SOLAR_PACKAGE, 0);
            if (info != null && info.versionName != null && !info.versionName.trim().isEmpty()) {
                return info.versionName.trim();
            }
        } catch (Exception ignored) {}
        return "";
    }

    public static int installedVersionCode(Context context) {
        if (context == null) return 0;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(SOLAR_PACKAGE, 0);
            if (info != null) return info.versionCode;
        } catch (Exception ignored) {}
        return 0;
    }

    public static String displayLabel(Context context) {
        String name = installedVersionName(context);
        if (name.isEmpty()) return "Not installed";
        return SolarUpdateClient.formatVersionLabel(name);
    }

    public static boolean isNightlyInstall(Context context) {
        String name = installedVersionName(context);
        return name != null && name.startsWith("nightly-");
    }
}
