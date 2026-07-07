package com.solar.launcher;

import com.solar.input.policy.StaleOverlayGate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StaleOverlayGateTest {

    @Test
    public void openingStaleCeilingIsLongerThanActiveWithoutUi() {
        assertTrue(StaleOverlayGate.OPENING_STALE_MS > StaleOverlayGate.ACTIVE_WITHOUT_UI_STALE_MS);
        assertEquals(5000L, StaleOverlayGate.OPENING_STALE_MS);
        assertEquals(2000L, StaleOverlayGate.ACTIVE_WITHOUT_UI_STALE_MS);
    }
}
