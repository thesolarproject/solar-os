package com.solar.launcher;

import android.content.Context;

/**
 * Force-stop Solar (main + :overlay) and relaunch HOME — recovery from stuck overlay/handoff.
 */
public final class SolarRestart {

    private SolarRestart() {}

    /** Dismiss overlay, flash "Restarting", then force-stop and cold-start {@link MainActivity} as HOME. */
    public static void restartApp(final Context ctx) {
        restartApp(ctx, null);
    }

    /**
     * 2026-07-05 — Same as {@link #restartApp(Context)} with caller Handler for hold-to-restart path.
     * Layman: show "Restarting" briefly, then relaunch Solar HOME.
     */
    public static void restartApp(final Context ctx, android.os.Handler handler) {
        if (ctx != null) {
            SolarRecoveryCoordinator.onSolarRestart(ctx.getApplicationContext());
        }
        SolarRescue.executeAfterHudFlash(ctx, ctx != null
                ? ExternalInputHandoff.getForegroundPackageName(ctx.getApplicationContext())
                : null, handler);
    }
}
