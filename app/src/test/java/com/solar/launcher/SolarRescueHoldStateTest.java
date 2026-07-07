package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SolarRescueHoldStateTest {

    @Test
    public void rockboxEligibleForRescueHold() {
        assertTrue(GlobalOverlayPolicy.shouldArmRescueHoldForPackage("org.rockbox"));
        assertFalse(GlobalOverlayPolicy.shouldArmRescueHoldForPackage("com.solar.launcher"));
    }

    @Test
    public void bareWmAndSystemShellEligibleForRescueHold() {
        assertTrue(GlobalOverlayPolicy.shouldArmRescueHoldForPackage(null));
        assertTrue(GlobalOverlayPolicy.shouldArmRescueHoldForPackage(""));
        assertTrue(GlobalOverlayPolicy.shouldArmRescueHoldForPackage("android"));
        assertTrue(GlobalOverlayPolicy.shouldArmRescueHoldForPackage("com.android.systemui"));
    }

    @Test
    public void holdDeadlineTenSeconds() {
        try {
            long now = 1_000_000L;
            SolarRescueHoldState.setNowForTest(now);
            SolarRescueHoldState.setHoldForTest(now + 10_000L, SolarRescueHoldState.KIND_BACK);
            assertEquals(10, SolarRescueHoldState.secondsRemaining());
            assertEquals(0, SolarRescueHoldState.hudCountdownValue());
            SolarRescueHoldState.setHoldForTest(now + 7500L, SolarRescueHoldState.KIND_BACK);
            assertEquals(0, SolarRescueHoldState.hudCountdownValue());
            SolarRescueHoldState.setHoldForTest(now + 2500L, SolarRescueHoldState.KIND_BACK);
            assertEquals(3, SolarRescueHoldState.hudCountdownValue());
            SolarRescueHoldState.setHoldForTest(now + 1500L, SolarRescueHoldState.KIND_BACK);
            assertEquals(2, SolarRescueHoldState.hudCountdownValue());
            SolarRescueHoldState.setHoldForTest(now + 400L, SolarRescueHoldState.KIND_BACK);
            assertEquals(1, SolarRescueHoldState.hudCountdownValue());
            SolarRescueHoldState.disarm();
            assertFalse(SolarRescueHoldState.isHoldActive());
        } finally {
            SolarRescueHoldState.resetHoldForTest();
        }
    }

    @Test
    public void restartingFlashPhase() {
        try {
            long now = 1_000_000L;
            SolarRescueHoldState.setNowForTest(now);
            SolarRescueHoldState.setHoldForTest(now + 100L, SolarRescueHoldState.KIND_BACK);
            SolarRescueHoldState.signalRestarting();
            assertTrue(SolarRescueHoldState.isHudRestarting());
            assertTrue(SolarRescueHoldState.shouldShowHud());
            assertEquals(0, SolarRescueHoldState.hudCountdownValue());
            SolarRescueHoldState.disarm();
            assertFalse(SolarRescueHoldState.isHudRestarting());
        } finally {
            SolarRescueHoldState.resetHoldForTest();
        }
    }

    @Test
    public void staleHudSecondIgnoredWhenDeadlineCleared() {
        try {
            long now = 1_000_000L;
            SolarRescueHoldState.setNowForTest(now);
            SolarRescueHoldState.setHoldForTest(0L, SolarRescueHoldState.KIND_BACK);
            SolarRescueHoldState.setHudSecondForTest(1);
            assertEquals(0, SolarRescueHoldState.hudCountdownValue());
            assertNull(SolarRescueHoldState.hudText(null));
        } finally {
            SolarRescueHoldState.resetHoldForTest();
        }
    }
}
