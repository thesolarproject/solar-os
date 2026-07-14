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

    /** 2026-07-14 — JJ/3P no longer get Solar POWER quick menu; stock GlobalActions owns them. */
    @Test
    public void jjExcludedFromY2PowerLongSolarOnly() {
        assertFalse(GlobalOverlayPolicy.shouldOfferPowerLongGlobalModalForTest(
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
    public void imeInactiveJjGetsBackLongReturnToSolar() {
        assertTrue(GlobalOverlayPolicy.shouldOfferBackLongGlobalModal(
                LauncherDefault.JJ_PACKAGE, false));
        assertTrue(GlobalOverlayPolicy.shouldArmRescueHoldForPackage(LauncherDefault.JJ_PACKAGE));
    }

    @Test
    public void rockboxExcludedForBackLongOnY1AndY2() {
        assertFalse(GlobalOverlayPolicy.shouldOfferGlobalModalForPackage("org.rockbox"));
    }

    @Test
    public void rockboxExcludedFromY2PowerLongSolarOnly() {
        assertFalse(GlobalOverlayPolicy.shouldOfferPowerLongGlobalModalForTest("org.rockbox", true));
        assertFalse(GlobalOverlayPolicy.shouldOfferPowerLongGlobalModalForTest("org.rockbox", false));
    }

    /** Y1/Y2 parity: third-party apps get BACK-long return-to-Solar; policy is device-family agnostic. */
    @Test
    public void thirdPartyEligibleRegardlessOfDeviceFamily() {
        assertTrue(GlobalOverlayPolicy.shouldOfferGlobalModalForPackage("com.android.calculator2"));
        assertTrue(GlobalOverlayPolicy.shouldOfferBackLongGlobalModal(
                "com.android.calculator2", false));
    }

    @Test
    public void imeAllowsBackLongReturnToSolarOverRockbox() {
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
    public void innioasisStockLauncherKeysBlockedWhileOverlayActive() {
        assertTrue(GlobalOverlayPolicy.shouldBlockForegroundKeysWhileOverlayActive(
                "com.innioasis.y1"));
        assertTrue(GlobalOverlayPolicy.shouldBlockForegroundKeysWhileOverlayActive(
                "com.innioasis.y2"));
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
    public void solarAndInnioasisMusicExcluded() {
        assertFalse(GlobalOverlayPolicy.shouldOfferGlobalModalForPackage("com.solar.launcher"));
        assertFalse(GlobalOverlayPolicy.shouldOfferGlobalModalForPackage("com.innioasis.music"));
    }

    @Test
    public void innioasisStockLauncherGetsBackLongReturnToSolarNotPowerMenu() {
        assertTrue(GlobalOverlayPolicy.shouldOfferBackLongGlobalModal("com.innioasis.y1", false));
        assertTrue(GlobalOverlayPolicy.shouldOfferBackLongGlobalModal("com.innioasis.y2", false));
        assertFalse(GlobalOverlayPolicy.shouldOfferPowerLongGlobalModalForTest(
                "com.innioasis.y1", true));
        assertTrue(GlobalOverlayPolicy.shouldOfferPowerLongGlobalModalForTest(
                "com.solar.launcher", true));
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
