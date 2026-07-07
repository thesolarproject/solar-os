package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Open entry for power/volume overlay — no signature permission (system_server + shell am start).
 */
public final class PowerOverlayOpenReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (!OverlayTriggers.ACTION_SHOW_OVERLAY_POWER.equals(action)
                && !OverlayTriggers.ACTION_SHOW_OVERLAY_VOLUME.equals(action)) {
            return;
        }
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("action", action);
            d.put("process", processName(context));
            DebugMenuLog.log("PowerOverlayOpenReceiver.onReceive", "open overlay", "H-RBX", d);
        } catch (Exception ignored) {}
        // #endregion
        OverlayHandoffRestoreReceiver.notifyPause(context);
        // Explicit component — starts :overlay service without waking MainActivity.
        Intent svc = new Intent(context, SolarOverlayService.class);
        svc.setAction(action);
        svc.setClassName(context, SolarOverlayService.class.getName());
        try {
            context.startService(svc);
        } catch (Exception e) {
            android.util.Log.w("PowerOverlayOpen", "startService failed: " + e.getMessage());
        }
    }

    /** Debug: confirm overlay open runs in :overlay, not main Solar process. */
    private static String processName(Context context) {
        try {
            int pid = android.os.Process.myPid();
            android.app.ActivityManager am = (android.app.ActivityManager)
                    context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null && am.getRunningAppProcesses() != null) {
                for (android.app.ActivityManager.RunningAppProcessInfo info
                        : am.getRunningAppProcesses()) {
                    if (info.pid == pid) return info.processName;
                }
            }
        } catch (Exception ignored) {}
        return "?";
    }
}
