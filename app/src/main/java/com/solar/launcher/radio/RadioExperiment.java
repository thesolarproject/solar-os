package com.solar.launcher.radio;

import android.content.SharedPreferences;

import com.solar.launcher.media.MediaSuiteHost;

/**
 * 2026-07-10 — FM radio + Internet radio behind Debug → Radio experiment (off by default).
 * Was: FM production always; only internet gated. Now: entire Radio feature is experimental.
 * Reversal: restore isFmProduction()=true and isInAppRadioUiEnabled()=true.
 */
public final class RadioExperiment {
    public static final String PREF_RADIO_EXPERIMENT = "solar_radio_experiment";

    private RadioExperiment() {}

    /** Master switch — FM + internet radio need Debug → Radio experiment. */
    public static boolean isEnabled(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_RADIO_EXPERIMENT, false);
    }

    /** FM browse / player / settings — same gate as internet (2026-07-10). */
    public static boolean isFmEnabled(SharedPreferences prefs) {
        return isEnabled(prefs);
    }

    /** @deprecated use {@link #isFmEnabled} — FM is no longer production-default. */
    public static boolean isFmProduction() {
        return false;
    }

    /** Internet radio hub row + net browse — same experiment pref. */
    public static boolean isInternetRadioEnabled(SharedPreferences prefs) {
        return isEnabled(prefs);
    }

    /** Hub + FM browse + internet browse screen ids. */
    public static boolean isRadioScreenState(int state) {
        return state >= MediaSuiteHost.STATE_RADIO && state <= MediaSuiteHost.STATE_RADIO_NET_BROWSE
                || state == MediaSuiteHost.STATE_RADIO_FM_PLAYER;
    }

    /** Any in-app Radio UI (FM or internet) when experiment is on. */
    public static boolean isInAppRadioUiEnabled(SharedPreferences prefs) {
        return isEnabled(prefs);
    }

    /** Block all radio screens when experiment off. 2026-07-10 */
    public static boolean isBlockedScreenState(int state, SharedPreferences prefs) {
        if (!isRadioScreenState(state)) return false;
        return !isEnabled(prefs);
    }

    /** Home Radio/FM — only when experiment on (caller should hide row otherwise). */
    public static int resolveRadioHomeTarget(int state, SharedPreferences prefs) {
        if (!isEnabled(prefs)) {
            return state;
        }
        if (state == MediaSuiteHost.STATE_RADIO && !isInternetRadioEnabled(prefs)) {
            return MediaSuiteHost.STATE_RADIO_FM_PLAYER;
        }
        // With unified experiment, hub is available; prefer FM player for simple home open.
        if (state == MediaSuiteHost.STATE_RADIO) {
            return MediaSuiteHost.STATE_RADIO_FM_PLAYER;
        }
        return state;
    }
}
