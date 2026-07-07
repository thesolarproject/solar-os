package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Entry point for Xposed bridge broadcasts — starts {@link SolarOverlayService}.
 */
public final class OverlayTriggerReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (!OverlayTriggers.ACTION_SHOW_OVERLAY_POWER.equals(action)
                && !OverlayTriggers.ACTION_SHOW_OVERLAY_VOLUME.equals(action)
                && !OverlayTriggers.ACTION_SHOW_OVERLAY_TOAST.equals(action)) {
            return;
        }
        Intent svc = new Intent(context, SolarOverlayService.class);
        svc.setAction(action);
        if (OverlayTriggers.ACTION_SHOW_OVERLAY_TOAST.equals(action)) {
            svc.putExtra(OverlayTriggers.EXTRA_TOAST_TEXT,
                    intent.getStringExtra(OverlayTriggers.EXTRA_TOAST_TEXT));
            svc.putExtra(OverlayTriggers.EXTRA_TOAST_DURATION_MS,
                    intent.getLongExtra(OverlayTriggers.EXTRA_TOAST_DURATION_MS, 2000L));
        }
        context.startService(svc);
    }
}
