package com.solar.launcher;

import android.content.SharedPreferences;

/**
 * 2026-07-14 — Debug experiment gate for full Bluetooth setup screens.
 * Layman: Y1/Y2 try Bluetooth by default; A5 waits until you flip Debug On.
 * Tech: default {@code !DeviceFeatures.isA5()}; SharedPreferences still override.
 * Was: always false default on every family. Reversal: default false again.
 */
public final class BluetoothExperiment {
    public static final String PREF_BLUETOOTH_EXPERIMENT = "solar_bluetooth_experiment";

    private BluetoothExperiment() {}

    /**
     * 2026-07-14 — Enabled unless user flipped Off; default On for Y1/Y2, Off for A5.
     * Callers without Context still use {@link DeviceFeatures} family pin / cache.
     */
    public static boolean isEnabled(SharedPreferences prefs) {
        if (prefs == null) return false;
        boolean defaultOn = defaultEnabledForFamily();
        return prefs.getBoolean(PREF_BLUETOOTH_EXPERIMENT, defaultOn);
    }

    /** Y1/Y2 On; A5 (and unknown) Off. */
    public static boolean defaultEnabledForFamily() {
        return !DeviceFeatures.isA5();
    }

    /** Pure test hook — pass family pin result without SharedPreferences. */
    static boolean isEnabledForTest(Boolean prefOrNull, boolean a5Family) {
        boolean defaultOn = !a5Family;
        if (prefOrNull == null) return defaultOn;
        return prefOrNull.booleanValue();
    }
}
