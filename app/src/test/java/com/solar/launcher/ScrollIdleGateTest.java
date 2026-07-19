package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-18 — ScrollIdleGate frame-drop / idle timing contract.
 */
public class ScrollIdleGateTest {

    @Test
    public void idleMsMatchesRockboxTalkWindow() {
        assertTrue(ScrollIdleGate.IDLE_MS >= 150L);
        assertTrue(ScrollIdleGate.IDLE_MS <= 220L);
    }

    @Test
    public void frameDropOnMultiStep() {
        ScrollIdleGate g = new ScrollIdleGate();
        assertTrue(g.shouldFrameDropPaint(3, 0));
        assertFalse(g.shouldFrameDropPaint(1, 0));
    }

    @Test
    public void frameDropOnPendingBacklog() {
        ScrollIdleGate g = new ScrollIdleGate();
        assertTrue(g.shouldFrameDropPaint(1, ScrollIdleGate.FRAME_DROP_PENDING));
        assertFalse(g.shouldFrameDropPaint(1, 0));
    }

    @Test
    public void spinningAfterMark() {
        ScrollIdleGate g = new ScrollIdleGate();
        assertFalse(g.isSpinning());
        g.markActivity();
        assertTrue(g.isSpinning());
        g.reset();
        assertFalse(g.isSpinning());
    }
}
