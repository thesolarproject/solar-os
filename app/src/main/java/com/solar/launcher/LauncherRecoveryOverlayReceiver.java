package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 2026-07-07 — Opens crash-loop recovery overlay from Xposed / companion broadcast fallback.
 */
public final class LauncherRecoveryOverlayReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!OverlayTriggers.ACTION_SHOW_OVERLAY_LAUNCHER_RECOVERY.equals(intent.getAction())) return;
        Intent svc = new Intent(context, SolarOverlayService.class);
        svc.setAction(OverlayTriggers.ACTION_SHOW_OVERLAY_LAUNCHER_RECOVERY);
        svc.putExtra(OverlayTriggers.EXTRA_RECOVERY_PROCESS,
                intent.getStringExtra(OverlayTriggers.EXTRA_RECOVERY_PROCESS));
        context.startService(svc);
    }
}
