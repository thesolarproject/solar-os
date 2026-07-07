package com.solar.launcher;

import android.content.Context;
import android.content.Intent;

/**
 * Keeps {@code :overlay} warm so Y2 lock-hold / GlobalActions can start {@link SolarOverlayService}
 * without launching {@link MainActivity} or force-starting the main Solar process.
 */
public final class SolarOverlayHost {

    private SolarOverlayHost() {}

    /** Idempotent — safe from boot, launcher switch scripts, and {@link LauncherPreference}. */
    public static void ensureStarted(Context context) {
        if (context == null) return;
        Intent intent = new Intent(context, SolarOverlayService.class);
        intent.setAction(OverlayTriggers.ACTION_OVERLAY_KEEPALIVE);
        try {
            context.getApplicationContext().startService(intent);
        } catch (Exception ignored) {}
    }

    /** Theme pick in main process — warm overlay bitmap cache with the new folder. */
    public static void reloadOverlayTheme(Context context) {
        if (context == null) return;
        Intent intent = new Intent(context, SolarOverlayService.class);
        intent.setAction(OverlayTriggers.ACTION_OVERLAY_THEME_RELOAD);
        try {
            context.getApplicationContext().startService(intent);
        } catch (Exception ignored) {}
    }
}
