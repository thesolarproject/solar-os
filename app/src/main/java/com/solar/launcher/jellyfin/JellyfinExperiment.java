package com.solar.launcher.jellyfin;

import android.content.SharedPreferences;

/**
 * 2026-07-14: Debug kill switch for Jellyfin music (off by default).
 * Layman: turn on under Settings → Debug before Music/Library show Jellyfin.
 * Technical: mirrors PlexExperiment; Soft gate on hub/settings/search when false.
 * Reversal: remove gates + this class; always show when prefs configured.
 */
public final class JellyfinExperiment {
    public static final String PREF_JELLYFIN_EXPERIMENT = "solar_jellyfin_experiment";

    private JellyfinExperiment() {}

    /** 2026-07-14: True when Debug → Jellyfin experiment is On. */
    public static boolean isEnabled(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_JELLYFIN_EXPERIMENT, false);
    }

    /** 2026-07-14: Persist Debug toggle (also called when PC companion saves). */
    public static void setEnabled(SharedPreferences prefs, boolean enabled) {
        if (prefs != null) {
            prefs.edit().putBoolean(PREF_JELLYFIN_EXPERIMENT, enabled).apply();
        }
    }
}
