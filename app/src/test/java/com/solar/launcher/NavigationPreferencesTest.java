package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 2026-07-06 — Infinite scroll wrap index math. */
public class NavigationPreferencesTest {

    @Test
    public void advanceIndex_clampsWhenDisabled() {
        assertEquals(-1, NavigationPreferences.advanceIndex(0, -1, 5, false));
        assertEquals(-1, NavigationPreferences.advanceIndex(4, 1, 5, false));
        assertEquals(2, NavigationPreferences.advanceIndex(1, 1, 5, false));
    }

    @Test
    public void advanceIndex_wrapsWhenEnabled() {
        assertEquals(4, NavigationPreferences.advanceIndex(0, -1, 5, true));
        assertEquals(0, NavigationPreferences.advanceIndex(4, 1, 5, true));
        assertTrue(NavigationPreferences.advanceIndex(2, 1, 5, true) == 3);
    }
}
