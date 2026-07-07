package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** System-wide Solar restart — triggered by Xposed BACK ultra-long or root evdev daemon. */
public final class SolarRestartReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!OverlayTriggers.ACTION_RESTART_SOLAR.equals(intent.getAction())) return;
        SolarRestart.restartApp(context);
    }
}
