package com.solar.launcher;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** JVM tier mutex — ANR native_error blocks USB; queue consumed after dismiss policy (2026-07-06). */
public class OverlayTierSchedulerTest {

    @After
    public void tearDown() {
        OverlayTierScheduler.resetForTest();
    }

    @Test
    public void nativeErrorTierDefersUsbSpawn() {
        OverlayTierScheduler.setActiveTier(OverlayTierScheduler.TIER_NATIVE_ERROR);
        assertTrue(OverlayTierScheduler.shouldDeferUsbSpawn());
        assertTrue(OverlayTierScheduler.shouldDeferUsbForTier(
                OverlayTierScheduler.TIER_NATIVE_ERROR));
    }

    @Test
    public void powerTierDoesNotDeferUsb() {
        OverlayTierScheduler.setActiveTier(OverlayTierScheduler.TIER_POWER);
        assertFalse(OverlayTierScheduler.shouldDeferUsbSpawn());
        assertFalse(OverlayTierScheduler.shouldDeferUsbForTier(
                OverlayTierScheduler.TIER_POWER));
    }

    @Test
    public void pendingUsbQueueTracksState() {
        OverlayTierScheduler.queuePendingUsbPrompt();
        assertTrue(OverlayTierScheduler.hasPendingUsbPrompt());
        OverlayTierScheduler.clearPendingUsbPrompt();
        assertFalse(OverlayTierScheduler.hasPendingUsbPrompt());
    }

    @Test
    public void consumePendingUsbWithoutContextClearsQueue() {
        OverlayTierScheduler.queuePendingUsbPrompt();
        assertFalse(OverlayTierScheduler.tryConsumePendingUsbPrompt(null));
        assertFalse(OverlayTierScheduler.hasPendingUsbPrompt());
    }

    @Test
    public void teardownClearsTierAndPendingUsb() {
        OverlayTierScheduler.setActiveTier(OverlayTierScheduler.TIER_NATIVE_ERROR);
        OverlayTierScheduler.queuePendingUsbPrompt();
        OverlayTierScheduler.onOverlayTeardown();
        assertFalse(OverlayTierScheduler.shouldDeferUsbSpawn());
        assertFalse(OverlayTierScheduler.hasPendingUsbPrompt());
    }
}
