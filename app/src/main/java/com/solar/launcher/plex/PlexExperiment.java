package com.solar.launcher.plex;

import android.content.SharedPreferences;

/**
 * 2026-07-14: Debug kill switch for Plex music (off by default).
 * Layman: turn on under Settings → Debug before Music/Library show Plex.
 * Technical: soft gate on hub/settings/search when false; PC /plex save enables it.
 * Reversal: remove gates + this class; always show when prefs configured.
 */
public final class PlexExperiment {
    public static final String PREF_PLEX_EXPERIMENT = "solar_plex_experiment";

    private PlexExperiment() {}

    /** 2026-07-14: True when Debug → Plex experiment is On. */
    public static boolean isEnabled(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_PLEX_EXPERIMENT, false);
    }

    /** 2026-07-14: Persist Debug toggle (also called when PC companion saves). */
    public static void setEnabled(SharedPreferences prefs, boolean enabled) {
        if (prefs != null) {
            prefs.edit().putBoolean(PREF_PLEX_EXPERIMENT, enabled).apply();
        }
    }
}
