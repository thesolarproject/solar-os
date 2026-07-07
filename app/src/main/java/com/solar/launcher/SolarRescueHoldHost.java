package com.solar.launcher;

import android.content.Context;
import android.content.Intent;

/**
 * 2026-07-05 — Keeps {@link SolarRescueHoldService} alive for cross-app rescue countdown HUD.
 * Layman: babysits the little “return to Solar” timer so it survives app crashes.
 * Technical: idempotent START_STICKY keepalive from boot, daemon, and hold-arm writers.
 */
public final class SolarRescueHoldHost {

    private SolarRescueHoldHost() {}

    /** Boot + app start — service polls sys.solar.rescue.hold_deadline until RAM exhaustion. */
    public static void ensureStarted(Context context) {
        if (context == null) return;
        Intent intent = new Intent(context, SolarRescueHoldService.class);
        intent.setAction(OverlayTriggers.ACTION_RESCUE_HOLD_KEEPALIVE);
        try {
            context.getApplicationContext().startService(intent);
        } catch (Exception ignored) {}
    }

    /** Hold armed — nudge :hold process to poll immediately (no waiting for watchdog). */
    public static void ping(Context context) {
        ensureStarted(context);
        Intent intent = new Intent(context, SolarRescueHoldService.class);
        intent.setAction(OverlayTriggers.ACTION_RESCUE_HOLD_TICK);
        try {
            context.getApplicationContext().startService(intent);
        } catch (Exception ignored) {}
    }
}
