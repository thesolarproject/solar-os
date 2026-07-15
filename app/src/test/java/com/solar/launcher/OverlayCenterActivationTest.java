package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-08 — Overlay Power chip must not restart on one held OK; KEY_UP + grace decide activation.
 */
public class OverlayCenterActivationTest {

    private static final long GRACE = OverlayCenterActivation.SUB_TIER_CENTER_GRACE_MS;

    @Test
    public void graceConstantMatchesAppMenuOrder() {
        // 2026-07-08 — Keep in lockstep with OverlayModalHost APP_MENU_CENTER_GRACE_MS.
        assertEquals(595L, GRACE);
    }

    @Test
    public void keyDownNeverActivates() {
        assertFalse(OverlayCenterActivation.shouldActivateOnEvent(
                false, false, 1000L, 0L, GRACE));
    }

    @Test
    public void autoRepeatNeverActivates() {
        assertFalse(OverlayCenterActivation.shouldActivateOnEvent(
                true, true, 1000L, 0L, GRACE));
        assertFalse(OverlayCenterActivation.shouldActivateOnEvent(
                false, true, 1000L, 0L, GRACE));
    }

    @Test
    public void keyUpActivatesWhenNoSubTierStamp() {
        assertTrue(OverlayCenterActivation.shouldActivateOnEvent(
                true, false, 1000L, 0L, GRACE));
    }

    @Test
    public void keyUpBlockedDuringSubTierGrace() {
        long opened = 1000L;
        assertFalse(OverlayCenterActivation.shouldActivateOnEvent(
                true, false, opened + 100L, opened, GRACE));
        assertFalse(OverlayCenterActivation.shouldActivateOnEvent(
                true, false, opened + GRACE - 1L, opened, GRACE));
    }

    @Test
    public void keyUpAllowedAfterSubTierGrace() {
        long opened = 1000L;
        assertTrue(OverlayCenterActivation.shouldActivateOnEvent(
                true, false, opened + GRACE, opened, GRACE));
        assertTrue(OverlayCenterActivation.shouldActivateOnEvent(
                true, false, opened + GRACE + 50L, opened, GRACE));
    }
}
