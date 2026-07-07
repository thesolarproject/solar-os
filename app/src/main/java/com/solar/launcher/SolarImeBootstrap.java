package com.solar.launcher;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

/**
 * 2026-07-06 — APK-side default IME bootstrap (mirrors ROM 99SolarInit.sh).
 * Layman: makes Solar the wheel keyboard for every app, not just after a ROM flash.
 * Technical: writes Secure enabled_input_methods + default_input_method when Solar IME missing.
 * Reversal: delete; rely on ROM init.d only (APK-only OTA loses third-party IME tray).
 */
public final class SolarImeBootstrap {

    private static final String SOLAR_IME = "com.solar.launcher/.SolarInputMethodService";

    private SolarImeBootstrap() {}

    /** Idempotent — no-op when Solar IME already default and listed. */
    public static void ensureDefaultIme(Context context) {
        if (context == null) return;
        try {
            String enabled = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS);
            if (enabled == null || !enabled.contains(SOLAR_IME)) {
                String merged = TextUtils.isEmpty(enabled) ? SOLAR_IME : enabled + ":" + SOLAR_IME;
                Settings.Secure.putString(context.getContentResolver(),
                        Settings.Secure.ENABLED_INPUT_METHODS, merged);
            }
            String current = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (!SOLAR_IME.equals(current)) {
                Settings.Secure.putString(context.getContentResolver(),
                        Settings.Secure.DEFAULT_INPUT_METHOD, SOLAR_IME);
            }
        } catch (Exception ignored) {
            // Fail-open — stock LatinIME remains until ROM init or user picks Solar in Settings.
        }
    }
}
