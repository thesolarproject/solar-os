package com.solar.launcher.globalcontext;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * 2026-07-06 — Routes overlay paint to Solar :overlay or companion fallback shell.
 * Layman: companion opens the menu; Solar supplies live rows when it is running.
 * Technical: Solar-first paint delegate until Phase 3 IPC ships full row bind.
 * Reversal: delete; Xposed targets SolarOverlayService directly again.
 */
public final class CompanionOverlayRouter {

    private static final String SOLAR_PKG = "com.solar.launcher";
    private static final String SOLAR_OVERLAY = SOLAR_PKG + ".SolarOverlayService";

    private CompanionOverlayRouter() {}

    /** True when Solar APK can paint the full ThemedContextMenu in :overlay. */
    public static boolean shouldDelegatePaintToSolar(Context ctx) {
        return CompanionSolarProbe.isSolarInstalled(ctx);
    }

    /** Phase 5 — companion always paints; Solar supplies live rows via IPC when running. */
    public static boolean startSolarOverlayPower(Context ctx) {
        if (ctx == null) return false;
        startCompanionPowerOverlay(ctx);
        return true;
    }

    /** Warm companion overlay host — Solar supplies IPC rows when alive. */
    public static void warmOverlayHost(Context ctx) {
        if (ctx == null) return;
        Intent keep = new Intent(CompanionOverlayTriggers.ACTION_OVERLAY_KEEPALIVE);
        keep.setComponent(new ComponentName(
                "com.solar.launcher.globalcontext",
                "com.solar.launcher.globalcontext.GlobalContextOverlayService"));
        try {
            ctx.startService(keep);
        } catch (Exception ignored) {}
    }

    /** Companion-owned power overlay — sole WM paint path (Phase 5). */
    public static void startCompanionPowerOverlay(Context ctx) {
        if (ctx == null) return;
        Intent overlay = new Intent(CompanionOverlayTriggers.ACTION_SHOW_OVERLAY_POWER);
        overlay.setComponent(new ComponentName(
                "com.solar.launcher.globalcontext",
                "com.solar.launcher.globalcontext.GlobalContextOverlayService"));
        try {
            ctx.startService(overlay);
        } catch (Exception ignored) {}
    }
}
