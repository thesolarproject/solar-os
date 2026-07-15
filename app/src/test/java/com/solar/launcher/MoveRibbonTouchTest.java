package com.solar.launcher;

import org.junit.Test;

/**
 * 2026-07-15 — Slot-step threshold math for touch drag reorder.
 * Layman: dragging past one row height counts as one step.
 */
public class MoveRibbonTouchTest {

    @Test
    public void stepsFromAccumulatedDy_slotThreshold() {
        if (MoveRibbonTouch.stepsFromAccumulatedDy(0f, 48) != 0) {
            throw new AssertionError("zero dy");
        }
        if (MoveRibbonTouch.stepsFromAccumulatedDy(47f, 48) != 0) {
            throw new AssertionError("under one slot");
        }
        if (MoveRibbonTouch.stepsFromAccumulatedDy(48f, 48) != 1) {
            throw new AssertionError("exact one slot");
        }
        if (MoveRibbonTouch.stepsFromAccumulatedDy(120f, 48) != 2) {
            throw new AssertionError("two slots");
        }
        if (MoveRibbonTouch.stepsFromAccumulatedDy(-96f, 48) != -2) {
            throw new AssertionError("up two slots");
        }
        if (MoveRibbonTouch.stepsFromAccumulatedDy(10f, 0) != 10) {
            throw new AssertionError("slot floor 1");
        }
    }
}
