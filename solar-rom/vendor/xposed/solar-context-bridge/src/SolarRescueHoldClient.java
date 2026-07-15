package com.solar.launcher.xposed.bridge;

import android.content.Context;
import android.os.SystemClock;

import de.robv.android.xposed.XposedHelpers;

/**
 * 2026-07-05 — Publishes rescue-hold deadline sysprops for {@code SolarRescueHoldService} HUD.
 * Layman: tells the on-screen timer that a 10s Back/power hold started.
 * Technical: sys.solar.rescue.hold_deadline + hold_kind; mirrors app {@code SolarRescueHoldState}.
 */
public final class SolarRescueHoldClient {

    private static final String SOLAR_PKG = "com.solar.launcher";
    /** 2026-07-05 — Companion :hold HUD when installed (Phase 2a). */
    private static final String COMPANION_PKG = "com.solar.launcher.globalcontext";
    private static final String COMPANION_HOLD = COMPANION_PKG + ".RescueHoldService";
    private static final String HOLD_DEADLINE = "sys.solar.rescue.hold_deadline";
    private static final String HOLD_KIND = "sys.solar.rescue.hold_kind";
    private static final String ACTION_TICK = "com.solar.launcher.action.RESCUE_HOLD_TICK";
    /** 2026-07-08 — Match GlobalInputPolicy: continuous 10s rescue execute. */
    private static final long RESCUE_MS =
            com.solar.input.policy.GlobalInputPolicy.RESCUE_EXECUTE_MS;
    private static final String HUD_SECOND = "sys.solar.rescue.hud_second";

    private SolarRescueHoldClient() {}

    /** BACK down in Rockbox/third-party — start countdown (short BACK still injected on UP). */
    public static void armBack(Context ctx) {
        arm(ctx, "back", 0L);
    }

    /** Y2 power down — same rescue tier. */
    public static void armPower(Context ctx) {
        arm(ctx, "power", 0L);
    }

    /** 2026-07-08 — HUD at 7s aligned to hold DOWN (10s total from hold start). */
    public static void armPowerFromHoldStart(Context ctx, long holdDownAtUptime) {
        arm(ctx, "power", holdDownAtUptime);
    }

    /** 2026-07-06 — BACK rescue HUD anchored to PWM hold DOWN. */
    public static void armBackFromHoldStart(Context ctx, long holdDownAtUptime) {
        arm(ctx, "back", holdDownAtUptime);
    }

    public static void disarm() {
        setProp(HOLD_DEADLINE, "0");
        setProp(HOLD_KIND, "");
        setProp(HUD_SECOND, "0");
    }

    /** 2026-07-05 — Brief "Restarting" flash before exec clears HUD. */
    public static void signalRestarting() {
        setProp(HUD_SECOND, String.valueOf(-1));
    }

    private static void arm(Context ctx, String kind, long holdDownAtUptime) {
        long deadline = holdDownAtUptime > 0L
                ? holdDownAtUptime + RESCUE_MS
                : SystemClock.uptimeMillis() + RESCUE_MS;
        setProp(HOLD_DEADLINE, String.valueOf(deadline));
        setProp(HOLD_KIND, kind != null ? kind : "back");
        setProp(HUD_SECOND, "0");
        pingHud(ctx);
    }

    private static void pingHud(Context ctx) {
        if (ctx == null) return;
        try {
            android.content.Intent svc = new android.content.Intent(ACTION_TICK);
            if (SolarOverlayClient.isCompanionInstalled(ctx)) {
                svc.setComponent(new android.content.ComponentName(COMPANION_PKG, COMPANION_HOLD));
            } else {
                svc.setComponent(new android.content.ComponentName(SOLAR_PKG,
                        SOLAR_PKG + ".SolarRescueHoldService"));
            }
            ctx.startService(svc);
        } catch (Throwable ignored) {}
    }

    private static void setProp(String key, String value) {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", null);
            XposedHelpers.callStaticMethod(sp, "set", key, value);
        } catch (Throwable ignored) {}
        try {
            Runtime.getRuntime().exec(new String[]{"sh", "-c",
                    "setprop " + key + " " + quote(value)});
        } catch (Throwable ignored) {}
    }

    private static String quote(String s) {
        if (s == null || s.length() == 0) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
