package com.solar.launcher;

import com.solar.input.policy.StaleOverlayGate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 2026-07-08 — Stale gate ceilings + stuck-shell BACK heal decision. */
public class StaleOverlayGateTest {

    @Test
    public void uiWithoutShellStaleCeilingIsShort() {
        assertEquals(500L, StaleOverlayGate.UI_WITHOUT_SHELL_STALE_MS);
        assertTrue(StaleOverlayGate.UI_WITHOUT_SHELL_STALE_MS
                < StaleOverlayGate.ACTIVE_WITHOUT_UI_STALE_MS);
    }

    /**
     * 2026-07-11 — Document grace: missing active_at no longer means instant wipe.
     * Pure constant check — host JVM cannot set SystemProperties.
     */
    @Test
    public void shellVisibleGraceIsPositive() {
        assertTrue(StaleOverlayGate.UI_WITHOUT_SHELL_STALE_MS > 0L);
    }

    @Test
    public void openingStaleCeilingIsLongerThanActiveWithoutUi() {
        assertTrue(StaleOverlayGate.OPENING_STALE_MS > StaleOverlayGate.ACTIVE_WITHOUT_UI_STALE_MS);
        assertEquals(5000L, StaleOverlayGate.OPENING_STALE_MS);
        assertEquals(2000L, StaleOverlayGate.ACTIVE_WITHOUT_UI_STALE_MS);
    }

    @Test
    public void armedOverlayDoesNotFireStuckHeal() {
        // Normal case: overlay receives input — app must not also dismiss on BACK.
        assertFalse(StaleOverlayGate.shouldDismissStuckShellOnBack(
                true, false, true, StaleOverlayGate.KEY_ACTION_DOWN, 5000L, 0L));
        assertFalse(StaleOverlayGate.shouldConsumeStuckShellBack(true, false, true));
    }

    @Test
    public void stuckShellFiresOnBackDown() {
        // Shell painted, gate disarmed — BACK DOWN clears ghost menu before hold can arm.
        assertTrue(StaleOverlayGate.shouldDismissStuckShellOnBack(
                false, false, true, StaleOverlayGate.KEY_ACTION_DOWN, 5000L, 0L));
        assertTrue(StaleOverlayGate.shouldConsumeStuckShellBack(false, false, true));
    }

    @Test
    public void stuckShellIgnoresBackUpForDismissFire() {
        // UP still consumed while shell_visible, but DISMISS itself is DOWN-only.
        assertFalse(StaleOverlayGate.shouldDismissStuckShellOnBack(
                false, false, true, StaleOverlayGate.KEY_ACTION_UP, 5000L, 0L));
        assertTrue(StaleOverlayGate.shouldConsumeStuckShellBack(false, false, true));
    }

    @Test
    public void postDismissCooldownSuppressesHeal() {
        assertFalse(StaleOverlayGate.shouldDismissStuckShellOnBack(
                false, true, true, StaleOverlayGate.KEY_ACTION_DOWN, 5000L, 0L));
        assertFalse(StaleOverlayGate.shouldConsumeStuckShellBack(false, true, true));
    }

    @Test
    public void debounceBlocksSecondFireWithinOneSecond() {
        long firstAt = 1000L;
        assertTrue(StaleOverlayGate.shouldDismissStuckShellOnBack(
                false, false, true, StaleOverlayGate.KEY_ACTION_DOWN, firstAt, 0L));
        assertFalse(StaleOverlayGate.shouldDismissStuckShellOnBack(
                false, false, true, StaleOverlayGate.KEY_ACTION_DOWN,
                firstAt + 500L, firstAt));
        assertTrue(StaleOverlayGate.shouldDismissStuckShellOnBack(
                false, false, true, StaleOverlayGate.KEY_ACTION_DOWN,
                firstAt + StaleOverlayGate.STUCK_SHELL_BACK_DEBOUNCE_MS, firstAt));
    }

    @Test
    public void noShellVisibleMeansNoHeal() {
        assertFalse(StaleOverlayGate.shouldDismissStuckShellOnBack(
                false, false, false, StaleOverlayGate.KEY_ACTION_DOWN, 5000L, 0L));
        assertFalse(StaleOverlayGate.shouldConsumeStuckShellBack(false, false, false));
    }
}
