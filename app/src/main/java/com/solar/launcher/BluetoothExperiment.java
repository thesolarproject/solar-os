package com.solar.launcher;

import android.content.SharedPreferences;

/**
 * 2026-07-14 — Gate for Solar’s built-in Bluetooth pairing / connection screen.
 * 2026-07-16 — Default On for every family except A5; Debug toggle still overrides.
 * Layman: Y1/Y2 open Solar Bluetooth by default; A5 stays on stock Settings until Debug On.
 * Tech: {@link #defaultEnabledForFamily()} = {@code !DeviceFeatures.isA5()};
 * SharedPreferences key only when user flipped the Debug row.
 * Was: always false default on every family. Reversal: default false again.
 */
public final class BluetoothExperiment {
    public static final String PREF_BLUETOOTH_EXPERIMENT = "solar_bluetooth_experiment";

    private BluetoothExperiment() {}

    /**
     * True when Solar’s Bluetooth screen should open (not stock Android Bluetooth settings).
     * Missing pref → family default: On for non-A5, Off for A5.
     */
    public static boolean isEnabled(SharedPreferences prefs) {
        boolean defaultOn = defaultEnabledForFamily();
        if (prefs == null) return defaultOn;
        return prefs.getBoolean(PREF_BLUETOOTH_EXPERIMENT, defaultOn);
    }

    /**
     * 2026-07-16 — Default On for all users except A5.
     * A5 keeps experiment Off until Debug → Bluetooth (experiment) is flipped On.
     * Unknown family resolves via {@link DeviceFeatures} (display size beats lying model=Y1).
     */
    public static boolean defaultEnabledForFamily() {
        return !DeviceFeatures.isA5();
    }

    /** Pure test hook — no SharedPreferences / DeviceFeatures cache. */
    static boolean isEnabledForTest(Boolean prefOrNull, boolean a5Family) {
        boolean defaultOn = !a5Family;
        if (prefOrNull == null) return defaultOn;
        return prefOrNull.booleanValue();
    }
}
