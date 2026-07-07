package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Unit tests for Rockbox crash-fallback grace — no device required. */
public class RockboxRestartGraceTest {

    @Test
    public void solarHome_alwaysDisablesRockbox() {
        assertTrue(RockboxRestartGrace.shouldDisableRockboxOnSolarStart(true, false));
        assertTrue(RockboxRestartGrace.shouldDisableRockboxOnSolarStart(true, true));
        assertFalse(RockboxRestartGrace.shouldExitEarlyForRockboxGrace(true, false));
    }

    @Test
    public void rockboxHome_intentionalNav_disablesRockbox() {
        assertTrue(RockboxRestartGrace.shouldDisableRockboxOnSolarStart(false, true));
        assertFalse(RockboxRestartGrace.shouldExitEarlyForRockboxGrace(false, true));
    }

    @Test
    public void rockboxHome_crashFallback_exitsEarlyWithoutDisable() {
        assertFalse(RockboxRestartGrace.shouldDisableRockboxOnSolarStart(false, false));
        assertTrue(RockboxRestartGrace.shouldExitEarlyForRockboxGrace(false, false));
    }

    @Test
    public void graceWindow_coversHousekeepingRestart() {
        // Rockbox self-restart is typically a few seconds; 20s leaves headroom on slow SD.
        assertTrue(RockboxRestartGrace.GRACE_MS >= 15_000L);
        assertEquals(500L, RockboxRestartGrace.POLL_MS);
    }
}
