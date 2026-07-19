package com.solar.launcher.ui;

import org.junit.After;
import org.junit.Test;

/**
 * 2026-07-18 — Context-hold throbber coexistence with existing UiBusy reasons.
 */
public class UiBusyContextHoldTest {

    @After
    public void tearDown() {
        UiBusy.resetForTest();
    }

    @Test
    public void contextHoldBeginEnd() {
        UiBusy.begin(UiBusy.REASON_CONTEXT_HOLD);
        if (!UiBusy.isBusy(UiBusy.REASON_CONTEXT_HOLD)) {
            throw new AssertionError("context hold busy");
        }
        UiBusy.end(UiBusy.REASON_CONTEXT_HOLD);
        if (UiBusy.isBusy()) throw new AssertionError("idle after end");
    }

    @Test
    public void otherBusyNotClearedByContextHoldEnd() {
        UiBusy.begin(UiBusy.REASON_SEARCH);
        UiBusy.begin(UiBusy.REASON_CONTEXT_HOLD);
        UiBusy.end(UiBusy.REASON_CONTEXT_HOLD);
        if (!UiBusy.isBusy(UiBusy.REASON_SEARCH)) {
            throw new AssertionError("search must remain");
        }
        if (UiBusy.isBusy(UiBusy.REASON_CONTEXT_HOLD)) {
            throw new AssertionError("context hold cleared");
        }
    }

    @Test
    public void clearDropsContextHoldLeavingOtherBusy() {
        // 2026-07-18 — Modal-open uses clear(); must not wipe search/etc.
        UiBusy.begin(UiBusy.REASON_SEARCH);
        UiBusy.begin(UiBusy.REASON_CONTEXT_HOLD);
        UiBusy.clear(UiBusy.REASON_CONTEXT_HOLD);
        if (UiBusy.isBusy(UiBusy.REASON_CONTEXT_HOLD)) {
            throw new AssertionError("context hold must be gone");
        }
        if (!UiBusy.isBusy(UiBusy.REASON_SEARCH)) {
            throw new AssertionError("search must remain after clear(context_hold)");
        }
    }
}
