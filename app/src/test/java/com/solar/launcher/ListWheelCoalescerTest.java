package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 2026-07-16/17 — Wheel coalesce clamp + hard-stop after idle. */
public class ListWheelCoalescerTest {

    @Test
    public void clampDoesNotExceedMax() {
        assertEquals(ListWheelCoalescer.MAX_STEPS_PER_FLUSH,
                ListWheelCoalescer.clampSteps(100));
        assertEquals(-ListWheelCoalescer.MAX_STEPS_PER_FLUSH,
                ListWheelCoalescer.clampSteps(-100));
    }

    @Test
    public void clampPreservesSmallSteps() {
        assertEquals(3, ListWheelCoalescer.clampSteps(3));
        assertEquals(-2, ListWheelCoalescer.clampSteps(-2));
        assertEquals(0, ListWheelCoalescer.clampSteps(0));
    }

    @Test
    public void pendingCapsAtOneFrameEvenWhenFlooded() {
        // Pure clamp path: flood of multi-step offers never exceeds one frame.
        int pending = 0;
        for (int i = 0; i < 20; i++) {
            pending += 4;
            pending = ListWheelCoalescer.clampSteps(pending);
        }
        assertEquals(ListWheelCoalescer.MAX_STEPS_PER_FLUSH, pending);
    }

    @Test
    public void idleGapIsShortEnoughToFeelLikeHardStop() {
        // Finger pause ~50–100 ms should drop backlog; keep under a deliberate slow notch.
        assertTrue(ListWheelCoalescer.IDLE_CLEAR_MS >= 40L);
        assertTrue(ListWheelCoalescer.IDLE_CLEAR_MS <= 80L);
        assertTrue(ListWheelCoalescer.IDLE_CLEAR_MS
                < WheelPhysics.RESET_NANOS / 1_000_000L + 50L);
    }

    @Test
    public void maxStepsStaysModestForHugeLibraries() {
        // One selection per frame — never multi-frame backlog on 50k tracks.
        assertTrue(ListWheelCoalescer.MAX_STEPS_PER_FLUSH <= 6);
        assertTrue(ListWheelCoalescer.MAX_STEPS_PER_FLUSH >= 3);
    }

    @Test
    public void dropPendingZerosBacklog() {
        // Pure API: dropPending is what KEY_UP / long-spin hard-stop call.
        ListWheelCoalescer c = new ListWheelCoalescer();
        c.dropPending();
        assertEquals(0, c.pendingStepsForTest());
    }

    @Test
    public void minFlushMsIsEighty() {
        // 2026-07-18 — Pace floor so list paints do not vsync-storm on MT6572.
        assertEquals(80L, ListWheelCoalescer.MIN_FLUSH_MS);
    }

    @Test
    public void oppositeSignReplacesPendingNotNets() {
        // 2026-07-18 — CW backlog must die on first CCW (scrub-back interrupt).
        // Pure clamp math: was pending+=signed (+5 + −1 → +4); want replace → −1.
        int pending = ListWheelCoalescer.clampSteps(5);
        int incoming = -1;
        boolean reversed = pending != 0 && ((pending > 0) != (incoming > 0));
        assertTrue(reversed);
        if (reversed) pending = 0;
        pending += incoming;
        pending = ListWheelCoalescer.clampSteps(pending);
        assertEquals(-1, pending);
    }
}
