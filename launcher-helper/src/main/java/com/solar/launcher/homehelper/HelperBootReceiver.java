package com.solar.launcher.homehelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 2026-07-06 — Boot hook: ensure helper package stays enabled after OTA/ROM prep.
 * Layman: after reboot, make sure the Home middle-man app can still run.
 * Technical: pm enable via shell when package is disabled (no Solar process required).
 */
public final class HelperBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        try {
            Runtime.getRuntime().exec(new String[] {
                    "sh", "-c", "pm enable " + com.solar.home.policy.HomeTargetPolicy.HELPER_PKG + " 2>/dev/null"
            });
        } catch (Exception ignored) {}
        LauncherEnforcerService.ensureStarted(context);
        LauncherSwitchExecutor.ensurePlatformDaemon(context);
    }
}
