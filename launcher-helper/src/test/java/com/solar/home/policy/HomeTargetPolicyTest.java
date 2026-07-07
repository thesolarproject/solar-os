package com.solar.home.policy;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Unit tests for shared HOME policy constants — no device required. */
public class HomeTargetPolicyTest {

    @Test
    public void normalizeTargetDefaultsUnknownToSolar() {
        assertEquals(HomeTargetPolicy.TARGET_SOLAR, HomeTargetPolicy.normalizeTarget(null));
        assertEquals(HomeTargetPolicy.TARGET_SOLAR, HomeTargetPolicy.normalizeTarget("bogus"));
        assertEquals(HomeTargetPolicy.TARGET_ROCKBOX,
                HomeTargetPolicy.normalizeTarget(HomeTargetPolicy.TARGET_ROCKBOX));
        assertEquals(HomeTargetPolicy.TARGET_JJ,
                HomeTargetPolicy.normalizeTarget(HomeTargetPolicy.TARGET_JJ));
        assertEquals(HomeTargetPolicy.TARGET_CUSTOM,
                HomeTargetPolicy.normalizeTarget(HomeTargetPolicy.TARGET_CUSTOM));
    }

    @Test
    public void parseComponentRoundTrip() {
        String flat = HomeTargetPolicy.flattenComponent("org.rockbox", "org.rockbox.RockboxActivity");
        assertEquals("org.rockbox/org.rockbox.RockboxActivity", flat);
        String[] parts = HomeTargetPolicy.parseComponent(flat);
        assertArrayEquals(new String[] { "org.rockbox", "org.rockbox.RockboxActivity" }, parts);
    }

    @Test
    public void isAlternateHomeTarget() {
        assertTrue(HomeTargetPolicy.isAlternateHomeTarget(HomeTargetPolicy.TARGET_ROCKBOX));
        assertTrue(HomeTargetPolicy.isAlternateHomeTarget(HomeTargetPolicy.TARGET_JJ));
    }

    @Test
    public void resolveLaunchComponentRoutesKnownTargets() {
        String[] solar = HomeTargetPolicy.resolveLaunchComponent("solar", "");
        assertArrayEquals(new String[] {
                HomeTargetPolicy.SOLAR_PKG, HomeTargetPolicy.SOLAR_ACTIVITY }, solar);
        String[] rockbox = HomeTargetPolicy.resolveLaunchComponent("rockbox", "");
        assertArrayEquals(new String[] {
                HomeTargetPolicy.ROCKBOX_PKG, HomeTargetPolicy.ROCKBOX_ACTIVITY }, rockbox);
        String[] jj = HomeTargetPolicy.resolveLaunchComponent("jj", "");
        assertArrayEquals(new String[] {
                HomeTargetPolicy.JJ_PKG, HomeTargetPolicy.JJ_ACTIVITY }, jj);
    }

    @Test
    public void flattenLaunchComponentMatchesResolve() {
        String flat = HomeTargetPolicy.flattenLaunchComponent("rockbox", "");
        assertEquals("org.rockbox/org.rockbox.RockboxActivity", flat);
    }

    @Test
    public void launcherPickerActionConstant() {
        assertEquals("com.solar.launcher.homehelper.action.SHOW_LAUNCHER_PICKER",
                HomeTargetPolicy.ACTION_SHOW_LAUNCHER_PICKER);
    }

    @Test
    public void helperHomeActivityConstantStable() {
        assertEquals("com.solar.launcher.homehelper.LauncherHomeActivity",
                HomeTargetPolicy.HELPER_HOME_ACTIVITY);
    }
}
