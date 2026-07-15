package com.solar.launcher;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-08 — USB tier takeover vs native-error queue ordering.
 * Layman: "app not responding" keeps the menu; USB waits; other prompts swap in place.
 */
public class OverlayTierSchedulerTest {

    @Before
    public void reset() {
        OverlayTierScheduler.resetForTest();
        OverlayTierScheduler.onOverlayTeardown();
    }

    @Test
    public void nativeErrorDefersUsbSpawn() {
        OverlayTierScheduler.setActiveTier(OverlayTierScheduler.TIER_NATIVE_ERROR);
        assertTrue(OverlayTierScheduler.shouldDeferUsbSpawn());
        OverlayTierScheduler.queuePendingUsbPrompt();
        assertTrue(OverlayTierScheduler.hasPendingUsbPrompt());
    }

    @Test
    public void powerTierDoesNotDeferUsb() {
        OverlayTierScheduler.setActiveTier(OverlayTierScheduler.TIER_POWER);
        assertFalse(OverlayTierScheduler.shouldDeferUsbSpawn());
    }

    @Test
    public void teardownClearsPendingUsb() {
        OverlayTierScheduler.queuePendingUsbPrompt();
        OverlayTierScheduler.onOverlayTeardown();
        assertFalse(OverlayTierScheduler.hasPendingUsbPrompt());
    }

    @Test
    public void shouldDeferUsbForTierHelper() {
        assertTrue(OverlayTierScheduler.shouldDeferUsbForTier(
                OverlayTierScheduler.TIER_NATIVE_ERROR));
        assertFalse(OverlayTierScheduler.shouldDeferUsbForTier(
                OverlayTierScheduler.TIER_USB));
    }
}
