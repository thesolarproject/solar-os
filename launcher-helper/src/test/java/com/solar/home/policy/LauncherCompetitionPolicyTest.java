package com.solar.home.policy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 2026-07-06 — HOME competition disable matrix per target. */
public class LauncherCompetitionPolicyTest {

    @Test
    public void packageForTarget_mapsKnownIds() {
        assertEquals(HomeTargetPolicy.SOLAR_PKG,
                LauncherCompetitionPolicy.packageForTarget(HomeTargetPolicy.TARGET_SOLAR));
        assertEquals(HomeTargetPolicy.ROCKBOX_PKG,
                LauncherCompetitionPolicy.packageForTarget(HomeTargetPolicy.TARGET_ROCKBOX));
        assertEquals(HomeTargetPolicy.JJ_PKG,
                LauncherCompetitionPolicy.packageForTarget(HomeTargetPolicy.TARGET_JJ));
    }

    @Test
    public void packagesToDisableForTarget_neverIncludesActive() {
        String[] solarOff = LauncherCompetitionPolicy.packagesToDisableForTarget(
                HomeTargetPolicy.TARGET_SOLAR);
        assertEquals(2, solarOff.length);
        String[] rockboxOff = LauncherCompetitionPolicy.packagesToDisableForTarget(
                HomeTargetPolicy.TARGET_ROCKBOX);
        assertEquals(1, rockboxOff.length);
        assertEquals(HomeTargetPolicy.JJ_PKG, rockboxOff[0]);
    }

    @Test
    public void isKnownLauncherPackage_stripsProcessSuffix() {
        assertTrue(LauncherCompetitionPolicy.isKnownLauncherPackage("com.solar.launcher:overlay"));
        assertFalse(LauncherCompetitionPolicy.isKnownLauncherPackage("com.android.systemui"));
    }

    @Test
    public void targetForPackage_roundTrips() {
        assertEquals(HomeTargetPolicy.TARGET_ROCKBOX,
                LauncherCompetitionPolicy.targetForPackage(HomeTargetPolicy.ROCKBOX_PKG));
    }
}
