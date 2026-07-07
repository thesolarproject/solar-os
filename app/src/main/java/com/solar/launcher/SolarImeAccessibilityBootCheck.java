package com.solar.launcher;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

/**
 * 2026-07-05 — Boot check: re-enable Solar IME accessibility fallback when ROM allows.
 * Layman: makes sure the paste-backup typing helper is turned on after updates.
 * Technical: writes enabled_accessibility_services when service missing from list.
 * Reversal: delete; rely on ROM 99SolarInit.sh only.
 */
public final class SolarImeAccessibilityBootCheck {

    private static final String SERVICE =
            "com.solar.launcher/.SolarImeAccessibilityService";

    private SolarImeAccessibilityBootCheck() {}

    /** Idempotent — no-op when already enabled or secure settings locked. */
    public static void ensureEnabled(Context context) {
        if (context == null) return;
        try {
            String cur = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur != null && cur.contains(SERVICE)) return;
            String merged = TextUtils.isEmpty(cur) ? SERVICE : cur + ":" + SERVICE;
            Settings.Secure.putString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, merged);
            Settings.Secure.putInt(context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (Exception ignored) {
            // Fail-open — stock IME remains; tier-3 unavailable until user enables in Settings.
        }
    }
}
