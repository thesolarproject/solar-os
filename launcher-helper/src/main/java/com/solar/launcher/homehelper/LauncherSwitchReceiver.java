package com.solar.launcher.homehelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.solar.home.policy.HomeTargetPolicy;
import com.solar.home.policy.LauncherCompetitionPolicy;
import com.solar.home.policy.LauncherErrorRecoveryPolicy;

/**
 * 2026-07-06 — Receives APPLY_HOME_TARGET and RESTART_ACTIVE_LAUNCHER from Solar/companion/Apps.
 * Layman: one front door for changing which app is your home screen.
 * Technical: runs solar-launcher-exec.sh; shows brief prompt during switch.
 * Reversal: remove receiver; Solar PowerActions owns switch again.
 */
public final class LauncherSwitchReceiver extends BroadcastReceiver {

    private static final String TAG = "LauncherSwitchRx";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        if (HomeTargetPolicy.ACTION_APPLY_HOME_TARGET.equals(action)) {
            String target = intent.getStringExtra(HomeTargetPolicy.EXTRA_HOME_TARGET);
            if (target == null) target = HomeTargetPolicy.TARGET_SOLAR;
            LauncherErrorRecoveryPolicy.armPendingKill(
                    HomeTargetPolicy.SOLAR_PKG, LauncherErrorRecoveryPolicy.REASON_SWITCH);
            LauncherSwitchPromptActivity.showSwitching(context, target);
            boolean ok = LauncherSwitchExecutor.switchToTarget(target);
            Log.i(TAG, "apply target=" + target + " ok=" + ok);
            syncSolarReceiver(context, target);
            LauncherEnforcerService.ensureStarted(context);
            return;
        }
        if (HomeTargetPolicy.ACTION_RESTART_ACTIVE_LAUNCHER.equals(action)) {
            String target = readTargetProperty();
            LauncherErrorRecoveryPolicy.armPendingKill(
                    LauncherCompetitionPolicy.packageForTarget(
                            HomeTargetPolicy.normalizeTarget(target)),
                    LauncherErrorRecoveryPolicy.REASON_RESTART);
            LauncherSwitchPromptActivity.showRestarting(context, target);
            boolean ok = LauncherSwitchExecutor.restartActive();
            Log.i(TAG, "restart-active target=" + target + " ok=" + ok);
            LauncherEnforcerService.ensureStarted(context);
        }
    }

    private static void syncSolarReceiver(Context context, String target) {
        Intent sync = new Intent("com.solar.launcher.action.SET_PREFERRED_HOME");
        sync.setComponent(new android.content.ComponentName(
                HomeTargetPolicy.SOLAR_PKG, "com.solar.launcher.LauncherHomeReceiver"));
        sync.putExtra("target", LauncherSwitchExecutor.normalizeSwitchArg(target));
        context.sendBroadcast(sync);
    }

    private static String readTargetProperty() {
        return LauncherHomeActivity.readSystemProperty(
                HomeTargetPolicy.PROP_HOME_TARGET, HomeTargetPolicy.TARGET_SOLAR);
    }
}
