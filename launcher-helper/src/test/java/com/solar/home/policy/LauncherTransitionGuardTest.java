package com.solar.home.policy;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 2026-07-06 — HOME enforcement pause during overlay / switch windows. */
public class LauncherTransitionGuardTest {

    @Test
    public void isLauncherPackage_includesRockboxAndJj() {
        assertTrue(LauncherTransitionGuard.isLauncherPackage("org.rockbox"));
        assertTrue(LauncherTransitionGuard.isLauncherPackage("com.themoon.y1"));
        assertFalse(LauncherTransitionGuard.isLauncherPackage("com.android.settings"));
    }

    @Test
    public void isLauncherTransitionActive_falseWhenDeadlineUnset() {
        assertFalse(LauncherTransitionGuard.isLauncherTransitionActive());
    }
}
