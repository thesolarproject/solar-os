package com.solar.launcher.globalcontext;

import android.os.SystemClock;

/**
 * 2026-07-05 — Rescue hold sysprop reader for companion :hold HUD.
 * Layman: reads the countdown timer Solar/Xposed wrote so companion can paint 3..2..1.
 * Technical: mirrors {@code SolarRescueHoldState} property names without RootShell dep.
 * Reversal: delete; companion reads Solar APK class via shared UID (not available).
 */
public final class CompanionRescueHoldState {

    public static final String HOLD_DEADLINE_PROPERTY = "sys.solar.rescue.hold_deadline";
    public static final String HOLD_KIND_PROPERTY = "sys.solar.rescue.hold_kind";
    public static final String HUD_SECOND_PROPERTY = "sys.solar.rescue.hud_second";

    /** 2026-07-06 — Match GlobalInputPolicy rescue tier (−30% from 10s). */
    public static final long RESCUE_HOLD_MS =
            com.solar.input.policy.GlobalInputPolicy.RESCUE_EXECUTE_MS;
    public static final int HUD_COUNTDOWN_SECONDS = 3;
    public static final int HUD_RESTARTING = -1;

    public static final String KIND_BACK = "back";
    public static final String KIND_POWER = "power";

    private CompanionRescueHoldState() {}

    /** Layman: big number text for bottom HUD chip; null when hidden. */
    public static String hudText(android.content.Context ctx) {
        if (ctx == null) return null;
        if (isHudRestarting()) {
            return ctx.getString(R.string.rescue_hold_restarting);
        }
        int sec = hudCountdownValue();
        if (sec > 0) {
            return ctx.getString(R.string.rescue_hold_countdown, sec);
        }
        return null;
    }

    public static boolean isHudRestarting() {
        return readHudSecondProp() == HUD_RESTARTING;
    }

    public static int secondsRemaining() {
        long deadline = readDeadlineUptime();
        if (deadline <= 0L) return 0;
        long remain = deadline - SystemClock.uptimeMillis();
        if (remain <= 0L) return 0;
        return (int) ((remain + 999L) / 1000L);
    }

    public static int hudCountdownValue() {
        if (isHudRestarting()) return 0;
        int remain = secondsRemaining();
        if (remain <= 0 || remain > HUD_COUNTDOWN_SECONDS) return 0;
        if (readDeadlineUptime() <= 0L) return 0;
        int prop = readHudSecondProp();
        if (prop >= 1 && prop <= HUD_COUNTDOWN_SECONDS) return prop;
        return remain;
    }

    public static boolean isHoldActive() {
        return secondsRemaining() > 0 || isHudRestarting();
    }

    /** Arm 10s rescue track — deadline anchored to hold DOWN when known. */
    public static void arm(String kind) {
        armFromHoldStart(kind, 0L);
    }

    /** 2026-07-06 — HUD at 7s stays on 10s total from original hold DOWN. */
    public static void armFromHoldStart(String kind, long holdDownAtUptime) {
        long deadline = holdDownAtUptime > 0L
                ? holdDownAtUptime + RESCUE_HOLD_MS
                : SystemClock.uptimeMillis() + RESCUE_HOLD_MS;
        SysPropHelper.set(HOLD_DEADLINE_PROPERTY, String.valueOf(deadline));
        SysPropHelper.set(HOLD_KIND_PROPERTY, kind != null ? kind : KIND_BACK);
        SysPropHelper.set(HUD_SECOND_PROPERTY, "0");
    }

    /** Finger up — hide HUD. */
    public static void disarm() {
        SysPropHelper.set(HOLD_DEADLINE_PROPERTY, "0");
        SysPropHelper.set(HOLD_KIND_PROPERTY, "");
        SysPropHelper.set(HUD_SECOND_PROPERTY, "0");
    }

    /** Brief flash before {@link RescueExecutor} runs. */
    public static void signalRestarting() {
        SysPropHelper.set(HUD_SECOND_PROPERTY, String.valueOf(HUD_RESTARTING));
    }

    private static long readDeadlineUptime() {
        try {
            return Long.parseLong(SysPropHelper.get(HOLD_DEADLINE_PROPERTY, "0"));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static int readHudSecondProp() {
        try {
            return Integer.parseInt(SysPropHelper.get(HUD_SECOND_PROPERTY, "0"));
        } catch (Exception e) {
            return 0;
        }
    }
}
