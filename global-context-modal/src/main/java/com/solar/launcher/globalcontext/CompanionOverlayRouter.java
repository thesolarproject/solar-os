package com.solar.launcher.globalcontext;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.solar.launcher.overlay.OverlayShellRouter;

/**
 * 2026-07-14 — Routes paint to the one shell (Solar ThemedContextMenu by default).
 * Layman: opens the same decorated menu as Solar Home — not a second chip look.
 * Technical: OverlayShellRouter; companion Chip only if companion_shell=1.
 * Was: companion always. Reversal: companion_shell=1.
 */
public final class CompanionOverlayRouter {

    private CompanionOverlayRouter() {}

    /** True when Solar ThemedContextMenu owns power/global paint. */
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

    /** Warm companion overlay host — USB/rescue SPOF; optional when chip path enabled. */
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

    /** Chip path power overlay — only when companion_shell=1. */
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

    /** Start any overlay action on the configured shell. */
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
