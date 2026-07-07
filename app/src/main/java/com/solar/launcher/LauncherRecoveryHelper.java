package com.solar.launcher;

import android.content.Context;

import com.solar.home.policy.LauncherErrorRecoveryPolicy;

/**
 * 2026-07-07 — App-side helpers for launcher crash recovery props.
 * Layman: clears crash counters when user chooses to keep Solar.
 * Technical: RootShell setprop bridge — policy JAR owns key names.
 */
public final class LauncherRecoveryHelper {

    private LauncherRecoveryHelper() {}

    /** User chose keep Solar — reset rolling window + pending kill hints. */
    public static void clearRecoveryState(Context context) {
        LauncherErrorRecoveryPolicy.clearCrashWindow();
        LauncherErrorRecoveryPolicy.clearPendingKill();
        if (context != null) {
            SolarRecoveryCoordinator.clearEmergencyState(context.getApplicationContext());
        }
    }

    /** Arm hooks before Java-initiated force-stop (helper broadcast path). */
    public static void armPendingKill(String pkg, String reason) {
        LauncherErrorRecoveryPolicy.armPendingKill(pkg, reason);
    }
}
