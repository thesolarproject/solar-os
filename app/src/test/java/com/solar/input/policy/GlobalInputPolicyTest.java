package com.solar.input.policy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 2026-07-06 — Policy JAR unit tests per modular-resilience-contract §3. */
public class GlobalInputPolicyTest {

    @Test
    public void powerTapPassthroughUnder500ms() {
        assertTrue(GlobalInputPolicy.shouldPassthroughPowerTap(100L));
        assertTrue(GlobalInputPolicy.shouldPassthroughPowerTap(379L));
        assertFalse(GlobalInputPolicy.shouldPassthroughPowerTap(400L));
    }

    @Test
    public void y2PowerModalIncludesSolarHome() {
        assertTrue(GlobalInputPolicy.shouldOfferPowerLongModal(
                GlobalInputPolicy.SOLAR_PKG, true));
    }

    @Test
    public void y2PowerModalIncludesRockbox() {
        assertTrue(GlobalInputPolicy.shouldOfferPowerLongModal(
                GlobalInputPolicy.ROCKBOX_PKG, true));
    }

    @Test
    public void y2PowerFailOpenSystemui() {
        assertTrue(GlobalInputPolicy.shouldFailOpenPowerFg("com.android.systemui"));
        assertTrue(GlobalInputPolicy.shouldFailOpenPowerFg(null));
    }

    @Test
    public void y2BackFailOpenSystemui() {
        assertTrue(GlobalInputPolicy.shouldFailOpenBackFg("com.android.systemui"));
        assertTrue(GlobalInputPolicy.shouldOfferBackLongModal(
                "com.android.systemui", true, false, false));
    }

    @Test
    public void y1RockboxBackPassthroughNormalMode() {
        assertTrue(GlobalInputPolicy.shouldPassthroughRockboxBack(
                199L, false, false, false));
        assertFalse(GlobalInputPolicy.shouldPassthroughRockboxBack(
                200L, false, false, false));
    }

    @Test
    public void timingTiersUnifiedFastSpawn() {
        assertEquals(200L, GlobalInputPolicy.THIRD_PARTY_LAUNCHER_MODAL_HOLD_MS);
        assertEquals(200L, GlobalInputPolicy.NAV_OWNED_LAUNCHER_MODAL_HOLD_MS);
        assertEquals(200L, GlobalInputPolicy.ROCKBOX_BACK_PASSTHROUGH_MS);
        assertEquals(130L, GlobalInputPolicy.GLOBAL_MODAL_HOLD_MS);
        assertEquals(130L, GlobalInputPolicy.THIRD_PARTY_MODAL_HOLD_MS);
        assertEquals(130L, GlobalInputPolicy.MODAL_HOLD_MS);
        assertEquals(300L, GlobalInputPolicy.CENTER_MENU_HOLD_MS);
        assertEquals(130L, GlobalInputPolicy.SOLAR_BACK_CONTEXT_HOLD_MS);
        assertEquals(4900L, GlobalInputPolicy.HUD_COUNTDOWN_START_MS);
        assertEquals(7000L, GlobalInputPolicy.RESCUE_EXECUTE_MS);
        assertEquals(18, GlobalInputPolicy.POLICY_REV);
    }

    @Test
    public void thirdPartyBackModalAtOneHundredThirtyMs() {
        assertEquals(130L, GlobalInputPolicy.backModalHoldMsForPackage("com.android.settings"));
        assertEquals(130L, GlobalInputPolicy.powerModalHoldMsForPackage("com.mediatek.camera"));
        assertEquals(150L, GlobalInputPolicy.overlayDismissGraceMsForPackage("com.android.settings"));
        assertEquals(130L, GlobalInputPolicy.backModalHoldMsForPackage("com.android.systemui"));
    }

    @Test
    public void navOwnedLauncherHoldTwoHundredMs() {
        assertEquals(200L, GlobalInputPolicy.backModalHoldMsForPackage(GlobalInputPolicy.ROCKBOX_PKG));
        assertEquals(200L, GlobalInputPolicy.backModalHoldMsForPackage(GlobalInputPolicy.JJ_PKG));
        assertEquals(200L, GlobalInputPolicy.powerModalHoldMsForPackage(GlobalInputPolicy.ROCKBOX_PKG));
        assertEquals(200L, GlobalInputPolicy.powerModalHoldMsForPackage(GlobalInputPolicy.JJ_PKG));
        assertEquals(200L, GlobalInputPolicy.overlayDismissGraceMsForPackage(GlobalInputPolicy.JJ_PKG));
        assertEquals(200L, GlobalInputPolicy.overlayDismissGraceMsForPackage(GlobalInputPolicy.ROCKBOX_PKG));
    }

    @Test
    public void y2RockboxBackPassthroughUntilTwoHundredMs() {
        assertTrue(GlobalInputPolicy.shouldPassthroughRockboxBack(
                199L, true, false, false));
        assertFalse(GlobalInputPolicy.shouldPassthroughRockboxBack(
                200L, true, false, false));
    }

    @Test
    public void rockboxPowerPassthroughUsesNavOwnedTier() {
        assertTrue(GlobalInputPolicy.shouldPassthroughNavOwnedLauncherPower(
                199L, GlobalInputPolicy.ROCKBOX_PKG, false));
        assertFalse(GlobalInputPolicy.shouldPassthroughNavOwnedLauncherPower(
                200L, GlobalInputPolicy.ROCKBOX_PKG, false));
    }

    @Test
    public void jjPowerPassthroughUsesNavOwnedTier() {
        assertTrue(GlobalInputPolicy.shouldPassthroughNavOwnedLauncherPower(
                199L, GlobalInputPolicy.JJ_PKG, false));
        assertFalse(GlobalInputPolicy.shouldPassthroughNavOwnedLauncherPower(
                200L, GlobalInputPolicy.JJ_PKG, false));
    }

    @Test
    public void navOwnedLauncherGetsBackLongModal() {
        assertFalse(GlobalInputPolicy.shouldOfferBackLongModal(
                GlobalInputPolicy.ROCKBOX_PKG, false, false, false));
        assertTrue(GlobalInputPolicy.shouldOfferBackLongModal(
                GlobalInputPolicy.JJ_PKG, true, false, false));
    }

    @Test
    public void launcherPickerAliasesBackLongEligibility() {
        assertTrue(GlobalInputPolicy.shouldOfferLauncherPickerOnBackHold(
                "com.android.settings", false, false, false));
        assertTrue(GlobalInputPolicy.shouldOfferLauncherPickerOnBackHold(
                GlobalInputPolicy.JJ_PKG, false, false, false));
    }

    @Test
    public void imeActiveAllowsBackModalOverRockbox() {
        assertTrue(GlobalInputPolicy.shouldOfferBackLongModal(
                GlobalInputPolicy.ROCKBOX_PKG, false, true, false));
    }

    @Test
    public void launcherPickerPowerHoldMatchesPowerModal() {
        assertTrue(GlobalInputPolicy.shouldOfferLauncherPickerOnPowerHold(
                GlobalInputPolicy.SOLAR_PKG, true));
        assertFalse(GlobalInputPolicy.shouldOfferLauncherPickerOnPowerHold(
                GlobalInputPolicy.SOLAR_PKG, false));
    }

    @Test
    public void rescueHoldArmsForNullForegroundAtBoot() {
        assertTrue(GlobalInputPolicy.shouldArmRescueHoldForPackage(null, 0));
        assertTrue(GlobalInputPolicy.shouldArmRescueHoldForPackage("", GlobalInputPolicy.KEYCODE_BACK));
        assertTrue(GlobalInputPolicy.shouldArmRescueHoldForPackage("android", GlobalInputPolicy.KEYCODE_BACK));
        assertFalse(GlobalInputPolicy.shouldArmRescueHoldForPackage(
                GlobalInputPolicy.SOLAR_PKG, GlobalInputPolicy.KEYCODE_BACK));
        assertTrue(GlobalInputPolicy.shouldArmRescueHoldForPackage(
                GlobalInputPolicy.SOLAR_PKG, GlobalInputPolicy.KEYCODE_POWER));
    }

    @Test
    public void genericHomeLauncherUsesFastModalHold() {
        assertEquals(130L, GlobalInputPolicy.backModalHoldMsForPackage("com.example.home"));
        assertFalse(GlobalInputPolicy.isThirdPartyHomeLauncher("com.example.home"));
        assertFalse(GlobalInputPolicy.isNavOwnedHomeLauncher("com.example.home"));
    }

    @Test
    public void thirdPartyHomeLauncherDetection() {
        assertTrue(GlobalInputPolicy.isThirdPartyHomeLauncher(GlobalInputPolicy.JJ_PKG));
        assertFalse(GlobalInputPolicy.isThirdPartyHomeLauncher(GlobalInputPolicy.ROCKBOX_PKG));
        assertTrue(GlobalInputPolicy.isNavOwnedHomeLauncher(GlobalInputPolicy.ROCKBOX_PKG));
    }
}
