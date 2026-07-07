package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Shell / switch-script entry: set preferred HOME and sync persist.solar.home.target.
 */
public final class LauncherHomeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!LauncherDefault.ACTION_SET_PREFERRED_HOME.equals(intent.getAction())) return;
        String target = intent.getStringExtra(LauncherDefault.EXTRA_HOME_TARGET);
        if (LauncherDefault.TARGET_ROCKBOX.equals(target)) {
            LauncherPreference.applyHomeTarget(context, LauncherDefault.TARGET_ROCKBOX);
        } else if (LauncherDefault.TARGET_JJ.equals(target)) {
            LauncherPreference.applyHomeTarget(context, LauncherDefault.TARGET_JJ);
        } else {
            LauncherPreference.applyHomeTarget(context, LauncherDefault.TARGET_SOLAR);
        }
    }
}
