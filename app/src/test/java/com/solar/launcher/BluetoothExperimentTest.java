package com.solar.launcher;

import org.junit.After;
import org.junit.Test;

/**
 * 2026-07-16 — Bluetooth in-app screen default On for all but A5.
 */
public class BluetoothExperimentTest {

    @After
    public void tearDown() {
        DeviceFeatures.resetCacheForTest();
    }

    @Test
    public void defaultOnForY1AndY2OffForA5() {
        DeviceFeatures.setCachedFamilyForTest("y1");
        if (!BluetoothExperiment.defaultEnabledForFamily()) {
            throw new AssertionError("Y1 default On");
        }
        DeviceFeatures.setCachedFamilyForTest("y2");
        if (!BluetoothExperiment.defaultEnabledForFamily()) {
            throw new AssertionError("Y2 default On");
        }
        DeviceFeatures.setCachedFamilyForTest("a5");
        if (BluetoothExperiment.defaultEnabledForFamily()) {
            throw new AssertionError("A5 default Off");
        }
    }

    @Test
    public void prefOverrideWins() {
        if (!BluetoothExperiment.isEnabledForTest(Boolean.TRUE, true)) {
            throw new AssertionError("A5 user can enable");
        }
        if (BluetoothExperiment.isEnabledForTest(Boolean.FALSE, false)) {
            throw new AssertionError("Y1 user can disable");
        }
        if (!BluetoothExperiment.isEnabledForTest(null, false)) {
            throw new AssertionError("Y1 unset pref → On");
        }
        if (BluetoothExperiment.isEnabledForTest(null, true)) {
            throw new AssertionError("A5 unset pref → Off");
        }
    }

    @Test
    public void nullPrefsUsesFamilyDefault() {
        DeviceFeatures.setCachedFamilyForTest("y1");
        if (!BluetoothExperiment.isEnabled(null)) {
            throw new AssertionError("null prefs Y1 On");
        }
        DeviceFeatures.setCachedFamilyForTest("a5");
        if (BluetoothExperiment.isEnabled(null)) {
            throw new AssertionError("null prefs A5 Off");
        }
    }
}
