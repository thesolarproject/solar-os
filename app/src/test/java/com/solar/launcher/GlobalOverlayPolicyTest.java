package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Global modal eligibility — Y1 BACK-long; Y2 BACK-long + power-hold over Rockbox. */
public class GlobalOverlayPolicyTest {

    @Test
    public void jjExcludedForBackLongOnY1AndY2() {
        assertFalse(GlobalOverlayPolicy.shouldOfferGlobalModalForPackage(LauncherDefault.JJ_PACKAGE));
    }

    @Test
    public void jjAllowedForY2PowerLongOnly() {
        assertTrue(GlobalOverlayPolicy.shouldOfferPowerLongGlobalModalForTest(
                LauncherDefault.JJ_PACKAGE, true));
        assertFalse(GlobalOverlayPolicy.shouldOfferPowerLongGlobalModalForTest(
                LauncherDefault.JJ_PACKAGE, false));
    }

    @Test
    public void jjKeysBlockedWhileOverlayActive() {
        assertTrue(GlobalOverlayPolicy.shouldBlockForegroundKeysWhileOverlayActive(
                LauncherDefault.JJ_PACKAGE));
    }

    @Test
    public void imeInactiveJjGetsBackLongModalAtFourSeconds() {
        assertTrue(GlobalOverlayPolicy.shouldOfferBackLongGlobalModal(
                LauncherDefault.JJ_PACKAGE, false));
        assertTrue(GlobalOverlayPolicy.shouldArmRescueHoldForPackage(LauncherDefault.JJ_PACKAGE));
    }

    @Test
    public void rockboxExcludedForBackLongOnY1AndY2() {
        assertFalse(GlobalOverlayPolicy.shouldOfferGlobalModalForPackage("org.rockbox"));
    }

    @Test
    public void rockboxAllowedForY2PowerLongOnly() {
        assertTrue(GlobalOverlayPolicy.shouldOfferPowerLongGlobalModalForTest("org.rockbox", true));
        assertFalse(GlobalOverlayPolicy.shouldOfferPowerLongGlobalModalForTest("org.rockbox", false));
    }

    /** Y1/Y2 parity: third-party apps get BACK-long; policy does not branch on device family. */
    @Test
    public void thirdPartyEligibleRegardlessOfDeviceFamily() {
        assertTrue(GlobalOverlayPolicy.shouldOfferGlobalModalForPackage("com.android.calculator2"));
    }

    @Test
    public void imeAllowsBackLongOverRockbox() {
        assertTrue(GlobalOverlayPolicy.shouldOfferBackLongGlobalModal("org.rockbox", true));
    }

    @Test
    public void imeInactiveRockboxNeverGetsBackLongModal() {
        assertFalse(GlobalOverlayPolicy.shouldOfferBackLongGlobalModal("org.rockbox", false));
        assertTrue(GlobalOverlayPolicy.shouldArmRescueHoldForPackage("org.rockbox"));
    }

    @Test
    public void imeBlocksBackLongGlobalModal() {
        assertFalse(GlobalOverlayPolicy.shouldBlockBackLongWhileImeActive());
    }

    @Test
    public void rockboxKeysBlockedWhileOverlayActive() {
        assertTrue(GlobalOverlayPolicy.shouldBlockForegroundKeysWhileOverlayActive("org.rockbox"));
    }

    @Test
    public void innioasisNotBlockedWhileOverlayActive() {
        assertFalse(GlobalOverlayPolicy.shouldBlockForegroundKeysWhileOverlayActive("com.innioasis.music"));
    }

    @Test
    public void thirdPartyGetsGlobalModal() {
        assertTrue(GlobalOverlayPolicy.shouldOfferGlobalModalForPackage("com.mediatek.videoplayer"));
    }

    @Test
    public void solarAndInnioasisExcluded() {
        assertFalse(GlobalOverlayPolicy.shouldOfferGlobalModalForPackage("com.solar.launcher"));
        assertFalse(GlobalOverlayPolicy.shouldOfferGlobalModalForPackage("com.innioasis.music"));
    }

    @Test
    public void rescueHoldArmsForBareWmNotSolar() {
        assertTrue(GlobalOverlayPolicy.shouldArmRescueHoldForPackage(null));
        assertTrue(GlobalOverlayPolicy.shouldArmRescueHoldForPackage("android"));
        assertFalse(GlobalOverlayPolicy.shouldArmRescueHoldForPackage("com.solar.launcher"));
    }

    @Test
    public void rescuePowerHoldArmsOnSolarY2Only() {
        assertTrue(GlobalOverlayPolicy.shouldArmRescueHoldForPackageForTest(
                "com.solar.launcher", true, true));
        assertFalse(GlobalOverlayPolicy.shouldArmRescueHoldForPackageForTest(
                "com.solar.launcher", true, false));
        assertFalse(GlobalOverlayPolicy.shouldArmRescueHoldForPackageForTest(
                "com.solar.launcher", false, true));
    }
}
