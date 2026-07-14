package com.solar.launcher;

import android.content.Context;
import android.content.Intent;

/**
 * 2026-07-14 — Warm Solar :overlay (sole shell) unless companion_shell Chip escape is on.
 * Layman: keep the themed system menu process ready so hold-Back is instant.
 * Technical: no-op only when OverlayShellRouter.useCompanionShell() (opt-in Chip).
 * Was: skipped whenever companion primary. Reversal: companion_shell=1 skips warm again.
 */
public final class SolarOverlayHost {

    private SolarOverlayHost() {}

    /** Idempotent — skipped when chip escape hatch owns the global WM shell. */
    public static void ensureStarted(Context context) {
        if (context == null) return;
        if (com.solar.launcher.overlay.OverlayShellRouter.useCompanionShell()) return;
        Intent intent = new Intent(context, SolarOverlayService.class);
        intent.setAction(OverlayTriggers.ACTION_OVERLAY_KEEPALIVE);
        try {
            context.getApplicationContext().startService(intent);
        } catch (Exception ignored) {}
    }

    /** Theme pick — warm sole Solar overlay cache (skipped for chip escape hatch). */
    public static void reloadOverlayTheme(Context context) {
        if (context == null) return;
        if (com.solar.launcher.overlay.OverlayShellRouter.useCompanionShell()) return;
        Intent intent = new Intent(context, SolarOverlayService.class);
        intent.setAction(OverlayTriggers.ACTION_OVERLAY_THEME_RELOAD);
        try {
            context.getApplicationContext().startService(intent);
        } catch (Exception ignored) {}
    }
}
