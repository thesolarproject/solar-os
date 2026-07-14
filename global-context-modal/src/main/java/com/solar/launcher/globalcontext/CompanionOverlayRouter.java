package com.solar.launcher.globalcontext;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.solar.launcher.overlay.OverlayShellRouter;

/**
 * 2026-07-08 — Routes all overlay paint to the companion shell (sole host).
 * Layman: every system menu opens in the helper APK, not inside Solar.
 * Technical: companion GlobalContextOverlayService; legacy_shell prop keeps Solar paint.
 * Was: companion painted POWER then retried Solar :overlay. Now: companion only.
 * Reversal: set persist.solar.overlay.legacy_shell=1.
 */
public final class CompanionOverlayRouter {

    private CompanionOverlayRouter() {}

    /** @deprecated Phase unification — always paint companion; Solar supplies IPC rows. */
    public static boolean shouldDelegatePaintToSolar(Context ctx) {
        return !OverlayShellRouter.useCompanionShell();
    }

    /** Open power tier on the preferred overlay shell. */
    public static boolean startSolarOverlayPower(Context ctx) {
        if (ctx == null) return false;
        if (shouldDelegatePaintToSolar(ctx)) {
            Intent solar = new Intent(CompanionOverlayTriggers.ACTION_SHOW_OVERLAY_POWER);
            solar.setComponent(new ComponentName(
                    OverlayShellRouter.SOLAR_PKG,
                    OverlayShellRouter.SOLAR_OVERLAY_SERVICE));
            try {
                ctx.startService(solar);
                return true;
            } catch (Exception ignored) {}
        }
        startCompanionPowerOverlay(ctx);
        return true;
    }

    /** Warm companion overlay host — Solar supplies IPC rows when alive. */
    public static void warmOverlayHost(Context ctx) {
        if (ctx == null) return;
        Intent keep = new Intent(CompanionOverlayTriggers.ACTION_OVERLAY_KEEPALIVE);
        keep.setComponent(new ComponentName(
                OverlayShellRouter.COMPANION_PKG,
                OverlayShellRouter.COMPANION_OVERLAY_SERVICE));
        try {
            ctx.startService(keep);
        } catch (Exception ignored) {}
    }

    /** Companion-owned power overlay — sole WM paint path. */
    public static void startCompanionPowerOverlay(Context ctx) {
        if (ctx == null) return;
        Intent overlay = new Intent(CompanionOverlayTriggers.ACTION_SHOW_OVERLAY_POWER);
        overlay.setComponent(new ComponentName(
                OverlayShellRouter.COMPANION_PKG,
                OverlayShellRouter.COMPANION_OVERLAY_SERVICE));
        try {
            ctx.startService(overlay);
        } catch (Exception ignored) {}
    }

    /** Start any overlay action on the configured shell (companion unless legacy). */
    public static boolean startOverlayAction(Context ctx, String action) {
        if (ctx == null || action == null) return false;
        Intent svc = new Intent(action);
        svc.setComponent(new ComponentName(
                OverlayShellRouter.overlayPackage(),
                OverlayShellRouter.overlayServiceClass()));
        try {
            ctx.startService(svc);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
