package com.solar.launcher;

import android.content.Context;

import java.io.File;

/**
 * 2026-07-06 — Whether JJ Launcher rows should appear in Settings and overlay picker.
 * Layman: hide JJ until it is installed or the player is online to download it.
 * Technical: installed = PM or /system/app blob; offer = installed OR ConnectivityHelper.isOnline.
 */
public final class JjLauncherAvailability {

    public static final String JJ_APK_URL =
            "https://thesolarproject.github.io/solar-update/jj_latest.apk";

    private JjLauncherAvailability() {}

    /** JJ registered in Package Manager. */
    public static boolean isInstalled(Context context) {
        return LauncherSwitch.isJjInstalled(context);
    }

    /** JJ on device or installable from /system/app. */
    public static boolean isAvailable(Context context) {
        return LauncherSwitch.isJjAvailable(context);
    }

    /** Show JJ launcher UI — offline users only when JJ is already installed. */
    public static boolean isOfferVisible(Context context) {
        if (isInstalled(context)) return true;
        return ConnectivityHelper.isOnline(context);
    }

    /** System partition JJ APK path (ROM bake optional). */
    public static File systemApkPath() {
        return new File("/system/app/com.themoon.y1.apk");
    }
}
