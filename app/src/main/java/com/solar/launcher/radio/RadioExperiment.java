package com.solar.launcher.radio;

import android.content.SharedPreferences;

import com.solar.launcher.media.MediaSuiteHost;

/**
 * 2026-07-15 — FM is production on all Solar devices; Internet radio stays Debug-gated.
 * Was (2026-07-10): entire Radio feature behind experiment (off by default).
 * Layman: FM Radio always works; online stations still opt-in under Debug.
 * Reversal: isFmEnabled()=isEnabled(); block FM screens when pref off.
 */
public final class RadioExperiment {
    /** Gates Internet radio + hub dual-row; FM ignores this pref. */
    public static final String PREF_RADIO_EXPERIMENT = "solar_radio_experiment";

    private RadioExperiment() {}

    /**
     * Debug experiment master — Internet radio browse only.
     * FM does not read this for availability.
     */
    public static boolean isEnabled(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_RADIO_EXPERIMENT, false);
    }

    /** FM browse / player / settings — always on (JJ-parity production path). */
    public static boolean isFmEnabled(SharedPreferences prefs) {
        return true;
    }

    /** FM ships production-default (not experiment). */
    public static boolean isFmProduction() {
        return true;
    }

    /** Internet radio hub row + net browse — Debug experiment. */
    public static boolean isInternetRadioEnabled(SharedPreferences prefs) {
        return isEnabled(prefs);
    }

    /** Hub + FM browse + internet browse + FM player screen ids. */
    public static boolean isRadioScreenState(int state) {
        return state >= MediaSuiteHost.STATE_RADIO && state <= MediaSuiteHost.STATE_RADIO_NET_BROWSE
                || state == MediaSuiteHost.STATE_RADIO_FM_PLAYER;
    }

    /** In-app FM UI always; internet only when experiment on. */
    public static boolean isInAppRadioUiEnabled(SharedPreferences prefs) {
        return true;
    }

    /**
     * Block only Internet browse when experiment off.
     * FM hub/player/browse always allowed.
     */
    public static boolean isBlockedScreenState(int state, SharedPreferences prefs) {
        if (state == MediaSuiteHost.STATE_RADIO_NET_BROWSE) {
            return !isInternetRadioEnabled(prefs);
        }
        return false;
    }

    /**
     * Home Radio — open FM player (JJ style) unless Internet experiment unlocks the hub.
     */
    public static int resolveRadioHomeTarget(int state, SharedPreferences prefs) {
        if (state != MediaSuiteHost.STATE_RADIO) return state;
        if (isInternetRadioEnabled(prefs)) {
            return MediaSuiteHost.STATE_RADIO;
        }
        return MediaSuiteHost.STATE_RADIO_FM_PLAYER;
    }
}
