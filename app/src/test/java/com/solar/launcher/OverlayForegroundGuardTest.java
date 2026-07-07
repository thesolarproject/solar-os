package com.solar.launcher;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Foreground restore skipped after explicit Solar navigation. */
public class OverlayForegroundGuardTest {

    @After
    public void tearDown() {
        OverlayForegroundGuard.resetForTest();
    }

    @Test
    public void markUserRequestedSolarNavigation_skipsRestoreFlag() {
        OverlayForegroundGuard.markUserRequestedSolarNavigation();
        assertTrue(OverlayForegroundGuard.isUserRequestedSolarNavigationForTest());
    }

    @Test
    public void reset_clearsExplicitNavFlag() {
        OverlayForegroundGuard.markUserRequestedSolarNavigation();
        OverlayForegroundGuard.resetForTest();
        assertFalse(OverlayForegroundGuard.isUserRequestedSolarNavigationForTest());
    }
}
