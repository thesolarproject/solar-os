package com.solar.launcher.radio;

import android.content.SharedPreferences;

import com.solar.launcher.media.MediaSuiteHost;

/**
 * 2026-07-16 — FM + Internet radio behind Debug experiment (off by default for new users).
 * Was (2026-07-15): FM production-always-on; Internet experiment-only.
 * Layman: Radio stays hidden until you flip the Debug switch; we ship FM when it is solid.
 * Reversal: isFmEnabled() return true; isBlockedScreenState only gate net browse.
 */
public final class RadioExperiment {
    /** Master gate for FM and Internet radio (Debug → Radio experiment). */
    public static final String PREF_RADIO_EXPERIMENT = "solar_radio_experiment";

    private RadioExperiment() {}

    /** Experiment master — off by default for new installs. */
    public static boolean isEnabled(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_RADIO_EXPERIMENT, false);
    }

    /** FM browse / player / settings — same gate as experiment (not production yet). */
    public static boolean isFmEnabled(SharedPreferences prefs) {
        return isEnabled(prefs);
    }

    /** FM is still experimental — not the production default. */
    public static boolean isFmProduction() {
        return false;
    }

    /** Internet radio hub row + net browse — same experiment pref. */
    public static boolean isInternetRadioEnabled(SharedPreferences prefs) {
        return isEnabled(prefs);
    }

    /** Hub + FM browse + internet browse + FM player screen ids. */
    public static boolean isRadioScreenState(int state) {
        return state >= MediaSuiteHost.STATE_RADIO && state <= MediaSuiteHost.STATE_RADIO_NET_BROWSE
                || state == MediaSuiteHost.STATE_RADIO_FM_PLAYER;
    }

    /** Any in-app radio UI (FM or internet) — experiment only. */
    public static boolean isInAppRadioUiEnabled(SharedPreferences prefs) {
        return isEnabled(prefs);
    }

    /**
     * Block all radio screens when experiment is off; when on, nothing radio-related is blocked here.
     */
    public static boolean isBlockedScreenState(int state, SharedPreferences prefs) {
        if (!isRadioScreenState(state)) return false;
        return !isEnabled(prefs);
    }

    /**
     * Home Radio tile — hub when experiment on; otherwise leave state (caller should not open radio).
     */
    public static int resolveRadioHomeTarget(int state, SharedPreferences prefs) {
        if (state != MediaSuiteHost.STATE_RADIO) return state;
        if (!isEnabled(prefs)) return state;
        if (isInternetRadioEnabled(prefs)) {
            return MediaSuiteHost.STATE_RADIO;
        }
        return MediaSuiteHost.STATE_RADIO_FM_PLAYER;
    }
}
