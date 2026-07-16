package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 2026-07-16 — First-session ready overlay extras and prefs contract. */
public class FirstSessionReadyGateTest {

    @Test
    public void keepReadyExtraConstant() {
        assertEquals("solar_keep_ready_overlay", FirstSessionReadyGate.EXTRA_KEEP_READY_OVERLAY);
    }

    @Test
    public void nullContextSafe() {
        assertFalse(FirstSessionReadyGate.shouldShowGettingReady(null));
        assertFalse(FirstSessionReadyGate.shouldShowPrepWizard(null));
        assertTrue(FirstSessionReadyGate.isUiReadyComplete(null));
        FirstSessionReadyGate.markUiReadyComplete(null);
        FirstSessionReadyGate.clearUiReady(null);
    }
}
