package com.solar.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * 2026-07-06 — Routes in-Solar context menus through companion WM overlay when available.
 * Layman: opens the quick menu in a separate helper process so a Solar crash won't kill it.
 * Technical: Phase 4 entry — power/global menu via companion; in-app fallback when helper missing.
 * Reversal: delete; MainActivity.showThemedContextMenu paints locally again.
 */
public final class CompanionContextMenuLauncher {

    private static final String COMPANION_PKG = "com.solar.launcher.globalcontext";
    private static final String COMPANION_OVERLAY =
            COMPANION_PKG + ".GlobalContextOverlayService";

    private CompanionContextMenuLauncher() {}

    public static boolean isCompanionInstalled(Context ctx) {
        if (ctx == null) return false;
        try {
            ctx.getPackageManager().getApplicationInfo(COMPANION_PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Y2 power-hold / unified global quick menu — prefer premium SolarOverlayService first, fallback to companion. */
    public static boolean openPowerQuickMenu(Context ctx) {
        if (ctx == null) return false;
        Intent solar = new Intent(OverlayTriggers.ACTION_SHOW_OVERLAY_POWER);
        solar.setComponent(new ComponentName(ctx.getPackageName(), "com.solar.launcher.SolarOverlayService"));
        try {
            ctx.startService(solar);
            return true;
        } catch (Exception ignored) {}
        if (isCompanionInstalled(ctx)) {
            Intent svc = new Intent(OverlayTriggers.ACTION_SHOW_OVERLAY_POWER);
            svc.setComponent(new ComponentName(COMPANION_PKG, COMPANION_OVERLAY));
            try {
                ctx.startService(svc);
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }
}
