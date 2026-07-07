package com.solar.launcher.radio;

import android.content.SharedPreferences;

import com.solar.launcher.media.MediaSuiteHost;

/**
 * FM radio is production; Internet radio stays behind debug experiment pref.
 * 2026-07-06 — FM always on home; experiment toggles net browse + hub only.
 */
public final class RadioExperiment {
    public static final String PREF_RADIO_EXPERIMENT = "solar_radio_experiment";

    private RadioExperiment() {}

    /** FM browse, FM NP, and FM settings are always available. */
    public static boolean isFmProduction() {
        return true;
    }

    /** Internet radio hub row + net browse — off until Debug → Radio experiment. */
    public static boolean isInternetRadioEnabled(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_RADIO_EXPERIMENT, false);
    }

    /** @deprecated use {@link #isInternetRadioEnabled} for net; FM no longer gated */
    public static boolean isEnabled(SharedPreferences prefs) {
        return isInternetRadioEnabled(prefs);
    }

    /** Hub + FM browse + internet browse screen ids. */
    public static boolean isRadioScreenState(int state) {
        return state >= MediaSuiteHost.STATE_RADIO && state <= MediaSuiteHost.STATE_RADIO_NET_BROWSE;
    }

    /** FM player + browse always in Solar; internet hub needs experiment. */
    public static boolean isInAppRadioUiEnabled(SharedPreferences prefs) {
        return true;
    }

    /** Net browse blocked when experiment off; FM screens always allowed. 2026-07-06 */
    public static boolean isBlockedScreenState(int state, SharedPreferences prefs) {
        return state == MediaSuiteHost.STATE_RADIO_NET_BROWSE
                && !isInternetRadioEnabled(prefs);
    }

    /** FM home opens player; full hub when internet experiment on. */
    public static int resolveRadioHomeTarget(int state, SharedPreferences prefs) {
        if (state == MediaSuiteHost.STATE_RADIO && !isInternetRadioEnabled(prefs)) {
            return MediaSuiteHost.STATE_RADIO_FM_PLAYER;
        }
        return state;
    }
}
