package com.solar.launcher;

import android.content.SharedPreferences;

/** Debug experiment gate for the full Bluetooth setup screens. */
public final class BluetoothExperiment {
    public static final String PREF_BLUETOOTH_EXPERIMENT = "solar_bluetooth_experiment";

    private BluetoothExperiment() {}

    /** Off by default on every model/user until Bluetooth setup bugs are ironed out. */
    public static boolean isEnabled(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_BLUETOOTH_EXPERIMENT, false);
    }
}
