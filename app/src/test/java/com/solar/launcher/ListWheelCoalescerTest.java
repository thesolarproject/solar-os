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
        // Finger pause ~100 ms should drop backlog; keep well under a deliberate slow notch.
        assertTrue(ListWheelCoalescer.IDLE_CLEAR_MS >= 50L);
        assertTrue(ListWheelCoalescer.IDLE_CLEAR_MS <= 150L);
        assertTrue(ListWheelCoalescer.IDLE_CLEAR_MS
                < WheelPhysics.RESET_NANOS / 1_000_000L + 50L);
    }

    @Test
    public void maxStepsStaysModestForHugeLibraries() {
        // One selection per frame — never multi-frame backlog on 50k tracks.
        assertTrue(ListWheelCoalescer.MAX_STEPS_PER_FLUSH <= 8);
        assertTrue(ListWheelCoalescer.MAX_STEPS_PER_FLUSH >= 3);
    }
}
