package com.solar.launcher;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Quick access menu chip visibility — Y2 hides Volume/Lock (hardware buttons). */
public class ContextQuickBarTest {

    @After
    public void tearDown() {
        DeviceFeatures.resetCacheForTest();
    }

    @Test
    public void y1ShowsVolumeAndLockChips() {
        DeviceFeatures.setCachedFamilyForTest("y1");
        assertTrue(MainActivity.isContextQuickVolumeChipVisibleForTest());
        assertTrue(MainActivity.isContextQuickLockChipVisibleForTest());
        assertTrue(MainActivity.isContextQuickPowerChipVisibleForTest());
    }

    @Test
    public void y2HidesVolumeAndLockChipsButKeepsPower() {
        DeviceFeatures.setCachedFamilyForTest("y2");
        assertFalse(MainActivity.isContextQuickVolumeChipVisibleForTest());
        assertFalse(MainActivity.isContextQuickLockChipVisibleForTest());
        assertTrue(MainActivity.isContextQuickPowerChipVisibleForTest());
    }
}
