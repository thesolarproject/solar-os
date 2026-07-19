package com.solar.launcher.stem;

import android.content.SharedPreferences;

/**
 * 2026-07-19 — Opt-in gate + active cloud stem provider.
 * Layman: Stem / Mix / Instrumental / Acapella stay hidden until you paste your own API key.
 * Technical: {@link LalalAccount#isUserConfigured} opts in; demo key alone is not enough.
 * Was: {@link LalalAccount#hasUsableKey} (demo unlocked menus). Reversal: gate on hasUsableKey again.
 */
public final class StemFeatures {

    public static final String PROVIDER_LALAL = "lalal";

    private StemFeatures() {}

    /**
     * User opted in with their own license key (not silent demo).
     * 2026-07-19
     */
    public static boolean isOptedIn(SharedPreferences prefs) {
        return LalalAccount.isUserConfigured(prefs);
    }

    /**
     * Active separator — LALAL when opted in; null otherwise.
     * Future: prefs pick provider id + per-vendor keys.
     * 2026-07-19
     */
    public static StemSeparatorProvider activeProvider(SharedPreferences prefs) {
        if (!isOptedIn(prefs)) return null;
        String key = LalalAccount.effectiveKey(prefs);
        if (key == null || key.length() < 8) return null;
        return new LalalStemSeparator(key);
    }

    /**
     * Show Stem Player / Mix rows — requires opt-in.
     * 2026-07-19
     */
    public static boolean showCloudStemMenus(SharedPreferences prefs) {
        return isOptedIn(prefs);
    }

    /**
     * Show Play Instrumental / Acapella — opt-in, or local cache already present.
     * 2026-07-19
     */
    public static boolean showSoloMenu(SharedPreferences prefs, boolean localReady) {
        return localReady || isOptedIn(prefs);
    }
}
