package com.solar.launcher;

import android.content.Context;
import android.provider.Settings;

/**
 * System brightness read/write for overlay service and context menu (no Activity window required).
 */
public final class SystemBrightnessControl {

    public static final int MIN = 10;
    public static final int MAX = 255;
    private static final int STEP = 15;

    private SystemBrightnessControl() {}

    public static int read(Context ctx) {
        if (ctx == null) return MAX;
        try {
            return Settings.System.getInt(ctx.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, MAX);
        } catch (Exception e) {
            return MAX;
        }
    }

    public static int adjust(int current, boolean up) {
        int cur = Math.max(MIN, Math.min(MAX, current));
        if (up) return Math.min(MAX, cur + STEP);
        return Math.max(MIN, cur - STEP);
    }

    /** Persist manual brightness — visible on next frame without Activity window attrs. */
    public static int apply(Context ctx, int level) {
        int clamped = Math.max(MIN, Math.min(MAX, level));
        if (ctx == null) return clamped;
        try {
            Settings.System.putInt(ctx.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(ctx.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, clamped);
        } catch (Exception ignored) {}
        return clamped;
    }

    public static int adjustAndApply(Context ctx, int current, boolean up) {
        return apply(ctx, adjust(current, up));
    }
}
