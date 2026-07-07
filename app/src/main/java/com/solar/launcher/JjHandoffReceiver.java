package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 2026-07-06 — Helper HOME route arms JJ wheel remap without opening MainActivity.
 * Layman: when you pick JJ as home, Solar wakes just enough to translate the scroll wheel.
 * Technical: starts SolarHandoffService with ACTION_ARM_JJ_HANDOFF from LauncherHomeActivity.
 * Reversal: remove broadcast; rely on 400ms RockboxForegroundMonitor poll only.
 */
public final class JjHandoffReceiver extends BroadcastReceiver {

    public static final String ACTION_ARM_JJ_HANDOFF =
            "com.solar.launcher.action.ARM_JJ_HANDOFF";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!ACTION_ARM_JJ_HANDOFF.equals(intent.getAction())) return;
        Intent svc = new Intent(context, SolarHandoffService.class);
        svc.setAction(ACTION_ARM_JJ_HANDOFF);
        try {
            context.startService(svc);
        } catch (Exception ignored) {}
        ExternalInputHandoff.armJjShim(context);
    }
}
