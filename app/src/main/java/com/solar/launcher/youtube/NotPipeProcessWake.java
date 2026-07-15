package com.solar.launcher.youtube;

import android.content.Context;
import android.content.Intent;

/**
 * 2026-07-06 — Starts notPipe process so Xposed bridge can register IPC receiver.
 * Layman: nudges notPipe awake in the background before Solar asks for YouTube data.
 * Technical: invisible MainActivity launch; bridge finishes it when solar_wake_only is set.
 * Reversal: delete; rely on user opening notPipe once (bad UX).
 */
public final class NotPipeProcessWake {

    private static final String MAIN_ACTIVITY = NotPipeIpc.NOTPIPE_PKG + ".MainActivity";
    private static volatile long lastWakeMs;

    private NotPipeProcessWake() {}

    /** Spawn notPipe process if IPC bridge may be asleep — cheap throttle. */
    public static void ensureAwake(Context ctx) {
        if (ctx == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastWakeMs < 1500L) return;
        lastWakeMs = now;
        Context app = ctx.getApplicationContext();
        // 2026-07-14 — Prefer sticky WakeService (survives wake-only MainActivity finish).
        // Layman: start the invisible keep-alive first; Activity nudge is backup.
        // Reversal: Activity-only wake as before.
        try {
            Intent svc = new Intent();
            svc.setClassName(NotPipeIpc.NOTPIPE_PKG, NotPipeIpc.NOTPIPE_PKG + ".SolarWakeService");
            app.startService(svc);
        } catch (Exception ignored) {}
        try {
            Intent wake = new Intent();
            wake.setClassName(NotPipeIpc.NOTPIPE_PKG, MAIN_ACTIVITY);
            wake.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            wake.putExtra(NotPipeIpc.EXTRA_SOLAR_WAKE_ONLY, true);
            app.startActivity(wake);
        } catch (Exception ignored) {}
    }
}
