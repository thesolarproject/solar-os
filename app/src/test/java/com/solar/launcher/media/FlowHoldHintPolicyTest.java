package com.solar.launcher.media;

import org.junit.Test;

/**
 * 2026-07-18 — Flow tip remaining ladder (Options → Flow ×3 → none).
 */
public class FlowHoldHintPolicyTest {

    @Test
    public void optionsUntilDismissed() {
        if (!"HOLD_BACK_OPTIONS".equals(
                FlowHoldHintPolicy.playerHintMode(false, false, 3, true))) {
            throw new AssertionError("options first");
        }
    }

    @Test
    public void flowAfterOptionsWhenRemaining() {
        if (!"HOLD_PLAY_FLOW".equals(
                FlowHoldHintPolicy.playerHintMode(true, false, 3, true))) {
            throw new AssertionError("flow tip");
        }
    }

    @Test
    public void noneWhenFlowDisabledOrDone() {
        if (!"NONE".equals(FlowHoldHintPolicy.playerHintMode(true, false, 3, false))) {
            throw new AssertionError("flow off");
        }
        if (!"NONE".equals(FlowHoldHintPolicy.playerHintMode(true, true, 0, true))) {
            throw new AssertionError("done");
        }
        if (!"NONE".equals(FlowHoldHintPolicy.playerHintMode(true, false, 0, true))) {
            throw new AssertionError("remaining 0");
        }
    }

    @Test
    public void afterPresentedCountsDownToDone() {
        int[] a = FlowHoldHintPolicy.afterPresented(3);
        if (a[0] != 2 || a[1] != 0) throw new AssertionError("3→2");
        a = FlowHoldHintPolicy.afterPresented(1);
        if (a[0] != 0 || a[1] != 1) throw new AssertionError("1→0 done");
        a = FlowHoldHintPolicy.afterPresented(0);
        if (a[0] != 0 || a[1] != 1) throw new AssertionError("0 stays done");
    }

    @Test
    public void clampRemaining() {
        if (FlowHoldHintPolicy.clampRemaining(-1) != 0) throw new AssertionError("neg");
        if (FlowHoldHintPolicy.clampRemaining(99) != FlowHoldHintPolicy.DEFAULT_REMAINING) {
            throw new AssertionError("cap");
        }
        if (FlowHoldHintPolicy.clampRemaining(2) != 2) throw new AssertionError("ok");
    }
}
