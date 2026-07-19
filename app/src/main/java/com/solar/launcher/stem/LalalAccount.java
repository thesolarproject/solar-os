package com.solar.launcher.stem;

import android.content.SharedPreferences;

/**
 * Lalal.ai license key — user prefs first, bundled demo like Deezer ARL.
 * Layman: ships a play-with-it key; Settings can replace it with yours.
 * Technical: never persists bundled key; PREF_USER_CONFIGURED gates UI copy.
 * 2026-07-18
 */
public final class LalalAccount {
    public static final String PREFS_NAME = "SOLAR_SETTINGS";
    public static final String PREF_LICENSE_KEY = "lalal_license_key";
    public static final String PREF_USER_CONFIGURED = "lalal_user_key_configured";
    /**
     * Experimental: decode+blend Melody stems to one WAV before play.
     * Default off — StemMixer plays piano/guitars/residual together (synced gain/loop).
     * 2026-07-19
     */
    public static final String PREF_STEM_PREMIX_EXPERIMENTAL = "stem_premix_experimental";

    /**
     * Bundled demo license — REMOVE / rotate before wide public launch.
     * Was: no Lalal integration. Reversal: clear constant + disable Stem Player entry.
     * 2026-07-18
     */
    private static final String BUNDLED_DEMO_KEY = "0aa329e86be9454b";

    private LalalAccount() {}

    /** True when the built-in demo key is present for silent fallback. */
    public static boolean hasBundledDemoKey() {
        return BUNDLED_DEMO_KEY != null && BUNDLED_DEMO_KEY.trim().length() >= 8;
    }

    /** Runtime-only demo key — never written to prefs. */
    public static String bundledDemoKey() {
        return hasBundledDemoKey() ? BUNDLED_DEMO_KEY.trim() : "";
    }

    /** User pasted their own key in Settings (not the silent demo). */
    public static boolean isUserConfigured(SharedPreferences prefs) {
        if (prefs == null) return false;
        if (!prefs.getBoolean(PREF_USER_CONFIGURED, false)) return false;
        String k = loadUserKey(prefs);
        return k.length() >= 8 && !k.equals(bundledDemoKey());
    }

    /** Session key: user key if configured, else bundled demo. */
    public static String effectiveKey(SharedPreferences prefs) {
        if (isUserConfigured(prefs)) return loadUserKey(prefs);
        return bundledDemoKey();
    }

    /** Can open Stem Player (demo or user key). */
    public static boolean hasUsableKey(SharedPreferences prefs) {
        String k = effectiveKey(prefs);
        return k != null && k.length() >= 8;
    }

    /** Settings subtitle — "Not configured" when using demo (Deezer-style). */
    public static String settingsStatusLabel(SharedPreferences prefs, String notConfigured,
            String configured) {
        if (isUserConfigured(prefs)) {
            return configured != null ? configured : "Configured";
        }
        return notConfigured != null ? notConfigured : "Not configured";
    }

    public static String loadUserKey(SharedPreferences prefs) {
        if (prefs == null) return "";
        String k = prefs.getString(PREF_LICENSE_KEY, "");
        return k != null ? k.trim() : "";
    }

    /** Save user key from Settings keyboard; empty clears back to demo. */
    public static void saveUserKey(SharedPreferences prefs, String key) {
        if (prefs == null) return;
        String k = key != null ? key.trim() : "";
        SharedPreferences.Editor ed = prefs.edit();
        if (k.length() < 8 || k.equals(bundledDemoKey())) {
            ed.remove(PREF_LICENSE_KEY);
            ed.putBoolean(PREF_USER_CONFIGURED, false);
        } else {
            ed.putString(PREF_LICENSE_KEY, k);
            ed.putBoolean(PREF_USER_CONFIGURED, true);
        }
        ed.commit();
    }

    /**
     * Premix Melody to one file (slow on Y1). Off = live multi-player zone 3.
     * Was: always premix. Reversal: remove pref; always live or always premix.
     * 2026-07-19
     */
    public static boolean isPremixExperimental(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_STEM_PREMIX_EXPERIMENTAL, false);
    }

    /** Toggle experimental Melody premix; returns new value. 2026-07-19 */
    public static boolean togglePremixExperimental(SharedPreferences prefs) {
        if (prefs == null) return false;
        boolean next = !isPremixExperimental(prefs);
        prefs.edit().putBoolean(PREF_STEM_PREMIX_EXPERIMENTAL, next).commit();
        return next;
    }

    public static void setPremixExperimental(SharedPreferences prefs, boolean on) {
        if (prefs == null) return;
        prefs.edit().putBoolean(PREF_STEM_PREMIX_EXPERIMENTAL, on).commit();
    }
}
