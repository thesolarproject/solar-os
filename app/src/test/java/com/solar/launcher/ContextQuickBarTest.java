package com.solar.launcher;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Quick access menu chip visibility — Y2 hides Volume/Sleep (hardware buttons). */
public class ContextQuickBarTest {

    @After
    public void tearDown() {
        DeviceFeatures.resetCacheForTest();
    }

    @Test
    public void y1ShowsVolumeAndSleepChips() {
        DeviceFeatures.setCachedFamilyForTest("y1");
        assertTrue(MainActivity.isContextQuickVolumeChipVisibleForTest());
        assertTrue(MainActivity.isContextQuickSleepChipVisibleForTest());
        assertTrue(MainActivity.isContextQuickPowerChipVisibleForTest());
    }

    @Test
    public void y2HidesVolumeAndSleepChipsButKeepsPower() {
        DeviceFeatures.setCachedFamilyForTest("y2");
        assertFalse(MainActivity.isContextQuickVolumeChipVisibleForTest());
        assertFalse(MainActivity.isContextQuickSleepChipVisibleForTest());
        assertTrue(MainActivity.isContextQuickPowerChipVisibleForTest());
    }

    /**
     * 2026-07-15 — Sleep/Zzz is rightmost after Volume (was Lock at index 1).
     * Reversal: expect sleepIndex == 1 and volumeIndex == 7.
     */
    @Test
    public void sleepChipIsRightOfVolume() {
        int volumeIndex = MainActivity.contextQuickVolumeChipIndexForTest();
        int sleepIndex = MainActivity.contextQuickSleepChipIndexForTest();
        if (volumeIndex != 6) throw new AssertionError("volume index expected 6 got " + volumeIndex);
        if (sleepIndex != 7) throw new AssertionError("sleep index expected 7 got " + sleepIndex);
        if (sleepIndex != volumeIndex + 1) {
            throw new AssertionError("sleep must be immediately after volume");
        }
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
