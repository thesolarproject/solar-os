package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Runs in the main Solar process — :overlay broadcasts here so wheel inject state
 * (dpad_mode + sys.solar.handoff.active) stays in sync across processes.
 */
public final class OverlayHandoffRestoreReceiver extends BroadcastReceiver {

    /** Tell main process to pause handoff before overlay keys are armed. */
    public static void notifyPause(Context context) {
        if (context == null) return;
        Intent arm = new Intent(OverlayTriggers.ACTION_OVERLAY_ARMED);
        arm.setComponent(new android.content.ComponentName(context,
                OverlayHandoffRestoreReceiver.class.getName()));
        try {
            context.sendBroadcast(arm);
        } catch (Exception ignored) {}
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || intent.getAction() == null) return;
        Context app = context.getApplicationContext();
        if (OverlayTriggers.ACTION_OVERLAY_ARMED.equals(intent.getAction())) {
            ExternalInputHandoff.pauseForGlobalOverlay();
            return;
        }
        if (OverlayTriggers.ACTION_OVERLAY_DISMISSED.equals(intent.getAction())) {
            ExternalInputHandoff.restoreAfterOverlayDismiss(app);
        }
    }
}
