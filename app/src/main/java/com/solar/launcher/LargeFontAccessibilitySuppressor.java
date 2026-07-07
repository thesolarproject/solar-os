package com.solar.launcher;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

/**
 * 2026-07-05 — Keep Accessibility “Large fonts” off on Y1/Y2 (480×360 wheel UI).
 * Layman: if the stock Android large-text toggle is on, reset font size to normal every boot and Solar start.
 * Technical: {@link Settings.System#FONT_SCALE} → 1.0 on API 17 (4.2.2) and 19 (4.4.4) only.
 * Reversal: stop calling from {@link BootReceiver} / {@code 99SolarInit.sh} to allow user large fonts again.
 */
public final class LargeFontAccessibilitySuppressor {

    private static final String TAG = "LargeFontSuppress";
    /** Normal font scale — Accessibility “Large fonts” uses ~1.15–1.3 on JB/KK. */
    static final float NORMAL_FONT_SCALE = 1.0f;
    /** Treat anything above this as large-font accessibility enabled. */
    static final float LARGE_FONT_THRESHOLD = 1.01f;
    /** {@link Settings.System#FONT_SCALE} string — stable on API 17+. */
    private static final String FONT_SCALE_KEY = "font_scale";

    private LargeFontAccessibilitySuppressor() {}

    /** Every boot / Solar start — idempotent reset when scale is enlarged. */
    public static void ensureNormalFontScale(Context context) {
        if (context == null || !appliesToDevice()) return;
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                resetIfLarge(app);
            }
        }, "LargeFontSuppress").start();
    }

    /** Y1/Y2 Innioasis players on Android 4.2.2 / 4.4.4 only. */
    static boolean appliesToDevice() {
        if (!DeviceFeatures.isY1() && !DeviceFeatures.isY2()) return false;
        int sdk = Build.VERSION.SDK_INT;
        return sdk == Build.VERSION_CODES.JELLY_BEAN_MR1
                || sdk == Build.VERSION_CODES.KITKAT;
    }

    /** Test hook — large scale detection without Settings provider. */
    static boolean isLargeFontScaleForTest(float scale) {
        return scale > LARGE_FONT_THRESHOLD;
    }

    /** Platform prep / blocking boot path — synchronous reset when scale is enlarged. */
    public static void applySync(Context context) {
        if (context == null || !appliesToDevice()) return;
        resetIfLarge(context.getApplicationContext());
    }

    /** Reset enlarged system font scale — ContentResolver first, then root settings CLI. */
    static void resetIfLarge(Context context) {
        if (context == null) return;
        float current = readFontScale(context);
        if (!isLargeFontScaleForTest(current)) return;
        if (writeFontScale(context, NORMAL_FONT_SCALE)) {
            SolarLog.i(TAG, "reset font_scale " + current + " -> " + NORMAL_FONT_SCALE);
            return;
        }
        if (DeviceFeatures.canRunRootShell()) {
            RootShell.run("settings put system " + FONT_SCALE_KEY + " "
                    + formatScale(NORMAL_FONT_SCALE));
            SolarLog.i(TAG, "reset font_scale via su " + current + " -> " + NORMAL_FONT_SCALE);
        }
    }

    private static float readFontScale(Context context) {
        try {
            return Settings.System.getFloat(context.getContentResolver(), Settings.System.FONT_SCALE,
                    NORMAL_FONT_SCALE);
        } catch (Exception ignored) {
            return NORMAL_FONT_SCALE;
        }
    }

    private static boolean writeFontScale(Context context, float scale) {
        try {
            return Settings.System.putFloat(context.getContentResolver(),
                    Settings.System.FONT_SCALE, scale);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String formatScale(float scale) {
        return String.valueOf(scale);
    }
}
