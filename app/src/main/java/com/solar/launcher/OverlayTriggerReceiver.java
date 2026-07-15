package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 2026-07-10 — Xposed bridge broadcasts → the ONE shell from OverlayShellRouter (companion).
 * Was: always SolarOverlayService (second modal path). Now: companion Global Quick Menu.
 * Reversal: hardcode SolarOverlayService.class if legacy_shell-only is required.
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
        Intent svc = new Intent(action);
        svc.setComponent(com.solar.launcher.overlay.OverlayShellRouter.overlayComponent());
        if (OverlayTriggers.ACTION_SHOW_OVERLAY_TOAST.equals(action)) {
            svc.putExtra(OverlayTriggers.EXTRA_TOAST_TEXT,
                    intent.getStringExtra(OverlayTriggers.EXTRA_TOAST_TEXT));
            svc.putExtra(OverlayTriggers.EXTRA_TOAST_DURATION_MS,
                    intent.getLongExtra(OverlayTriggers.EXTRA_TOAST_DURATION_MS, 2000L));
        }
        try {
            context.startService(svc);
        } catch (Exception ignored) {}
    }
}
