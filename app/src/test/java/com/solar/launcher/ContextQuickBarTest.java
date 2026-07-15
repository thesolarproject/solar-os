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

    /**
     * 2026-07-14 — Landscape never stacks quick chips in two rows (Y1 single strip at 240p).
     * Portrait A5 / narrow / Y1 portrait experiment may use two rows.
     */
    @Test
    public void twoRowQuickBarPortraitOnly() {
        assertFalse(A5PortraitChrome.useTwoRowQuickBar(null));
        // Landscape buffer: never two-row even if A5/narrow eligible.
        assertFalse(A5PortraitChrome.useTwoRowQuickBar(false, true));
        // Portrait A5/narrow/Y1-portrait eligible: two-row.
        assertTrue(A5PortraitChrome.useTwoRowQuickBar(true, true));
        // Portrait but not eligible: single row.
        assertFalse(A5PortraitChrome.useTwoRowQuickBar(true, false));
    }
}
