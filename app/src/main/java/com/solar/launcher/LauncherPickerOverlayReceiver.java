package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Xposed hook replaces stock HOME ResolverActivity → Solar launcher picker overlay.
 */
public final class LauncherPickerOverlayReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!OverlayTriggers.ACTION_SHOW_OVERLAY_LAUNCHER_PICKER.equals(intent.getAction())) return;
        Intent svc = new Intent(context, SolarOverlayService.class);
        svc.setAction(OverlayTriggers.ACTION_SHOW_OVERLAY_LAUNCHER_PICKER);
        context.startService(svc);
    }
}
