package com.solar.launcher;

import android.content.Context;
import android.content.Intent;

import com.solar.home.policy.HomeTargetPolicy;

/**
 * 2026-07-06 — Thin client: Solar UI broadcasts HOME switch/restart to SolarHomeHelper.
 * Layman: Settings and overlay ask the helper to change home app — not Solar directly.
 * Technical: APPLY_HOME_TARGET / RESTART_ACTIVE_LAUNCHER intents to helper receiver.
 * Reversal: call PowerActions.switchTo* / LauncherPreference.applyHomeTarget directly again.
 */
public final class LauncherHelperClient {

    private LauncherHelperClient() {}

    /** Commit HOME target via helper root script + prompt. */
    public static void applyHomeTarget(Context context, String target, String source) {
        if (context == null || target == null) return;
        Intent i = new Intent(HomeTargetPolicy.ACTION_APPLY_HOME_TARGET);
        i.setPackage(HomeTargetPolicy.HELPER_PKG);
        i.putExtra(HomeTargetPolicy.EXTRA_HOME_TARGET, target);
        if (source != null) i.putExtra(HomeTargetPolicy.EXTRA_HOME_SOURCE, source);
        context.sendBroadcast(i);
    }

    /** Force-stop + cold start active launcher — never pm-disable active package. */
    public static void restartActiveLauncher(Context context, String source) {
        if (context == null) return;
        Intent i = new Intent(HomeTargetPolicy.ACTION_RESTART_ACTIVE_LAUNCHER);
        i.setPackage(HomeTargetPolicy.HELPER_PKG);
        if (source != null) i.putExtra(HomeTargetPolicy.EXTRA_HOME_SOURCE, source);
        context.sendBroadcast(i);
    }

    /** Ensure helper watchdog + platform supervisor are running. */
    public static void ensureHelperRunning(Context context) {
        if (context == null) return;
        try {
            context.startService(new Intent().setClassName(
                    HomeTargetPolicy.HELPER_PKG,
                    "com.solar.launcher.homehelper.LauncherEnforcerService"));
        } catch (Exception ignored) {}
        try {
            Runtime.getRuntime().exec(new String[] {
                    "su", "-c", "sh /system/etc/solar/solar-platform-daemon.sh &"
            });
        } catch (Exception ignored) {}
    }
}
