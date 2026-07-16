package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** 2026-07-16 — Wheel coalesce clamp for long-list backlog catch-up. */
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
}
