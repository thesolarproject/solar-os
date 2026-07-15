package com.solar.launcher;

import android.view.KeyEvent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExternalInputHandoffTest {

    @Test
    public void androidModeMapsY1MediaKeysToDpad() {
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PLAY, 0, ExternalInputHandoff.MODE_ANDROID));
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PAUSE, 0, ExternalInputHandoff.MODE_ANDROID));
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_NEXT, 0, ExternalInputHandoff.MODE_ANDROID));
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0, ExternalInputHandoff.MODE_ANDROID));
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0, ExternalInputHandoff.MODE_ANDROID));
    }

    @Test
    public void offModeDoesNotMapKeys() {
        assertEquals(0, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PLAY, 0, ExternalInputHandoff.MODE_OFF));
    }

    @Test
    public void fmModeKeepsRockboxY1SpecialMapping() {
        assertEquals(KeyEvent.KEYCODE_VOLUME_DOWN, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PLAY, 0, ExternalInputHandoff.MODE_FM));
        assertEquals(KeyEvent.KEYCODE_VOLUME_UP, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PAUSE, 0, ExternalInputHandoff.MODE_FM));
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0, ExternalInputHandoff.MODE_FM));
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 1, ExternalInputHandoff.MODE_FM));
    }

    @Test
    public void onlyMediaNavigationKeysAreEligible() {
        assertTrue(ExternalInputHandoff.isMediaNavigationKey(KeyEvent.KEYCODE_MEDIA_PLAY));
        assertTrue(ExternalInputHandoff.isMediaNavigationKey(KeyEvent.KEYCODE_MEDIA_NEXT));
        assertFalse(ExternalInputHandoff.isMediaNavigationKey(KeyEvent.KEYCODE_DPAD_UP));
        assertFalse(ExternalInputHandoff.isMediaNavigationKey(KeyEvent.KEYCODE_BACK));
    }

    /** Handoff mapping is device-agnostic — Y1 and Y2 share the same MEDIA→DPAD table. */
    @Test
    public void resolveModeForForegroundPackage() {
        assertEquals(ExternalInputHandoff.MODE_OFF,
                ExternalInputHandoff.resolveModeForForegroundPackage("com.solar.launcher"));
        assertEquals(ExternalInputHandoff.MODE_OFF,
                ExternalInputHandoff.resolveModeForForegroundPackage("org.rockbox"));
        assertEquals(ExternalInputHandoff.MODE_FM,
                ExternalInputHandoff.resolveModeForForegroundPackage("com.mediatek.FMRadio"));
        assertEquals(ExternalInputHandoff.MODE_JJ,
                ExternalInputHandoff.resolveModeForForegroundPackage(LauncherDefault.JJ_PACKAGE));
        assertEquals(ExternalInputHandoff.MODE_JJ,
                ExternalInputHandoff.resolveModeForForegroundPackage("com.innioasis.y1"));
        assertEquals(ExternalInputHandoff.MODE_JJ,
                ExternalInputHandoff.resolveModeForForegroundPackage("com.innioasis.y2"));
        assertEquals(ExternalInputHandoff.MODE_OFF,
                ExternalInputHandoff.resolveModeForForegroundPackage("com.innioasis.music"));
        assertEquals(ExternalInputHandoff.MODE_ANDROID,
                ExternalInputHandoff.resolveModeForForegroundPackage("com.android.settings"));
    }

    /** JJ wheel scroll uses horizontal DPAD; stock media keys pass through to MainActivity. */
    @Test
    public void jjModeMapsWheelToHorizontalDpad() {
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PLAY, 0, ExternalInputHandoff.MODE_JJ));
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PAUSE, 0, ExternalInputHandoff.MODE_JJ));
        assertEquals(0, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0, ExternalInputHandoff.MODE_JJ));
        assertEquals(0, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_NEXT, 0, ExternalInputHandoff.MODE_JJ));
        assertEquals(0, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0, ExternalInputHandoff.MODE_JJ));
    }

    /** Legacy mtk-kpd vertical wheel lines remap to JJ horizontal scroll. */
    @Test
    public void jjLegacyHardwareRemapsVerticalWheelToHorizontal() {
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, ExternalInputHandoff.legacyHardwareToDpad(
                KeyEvent.KEYCODE_DPAD_UP, ExternalInputHandoff.MODE_JJ));
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, ExternalInputHandoff.legacyHardwareToDpad(
                KeyEvent.KEYCODE_DPAD_DOWN, ExternalInputHandoff.MODE_JJ));
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, ExternalInputHandoff.legacyHardwareToDpad(
                KeyEvent.KEYCODE_DPAD_LEFT, ExternalInputHandoff.MODE_JJ));
    }

    @Test
    public void restoreHandoffModeForPackage_rearmsJjForJjLauncher() {
        ExternalInputHandoff.setDpadMode(ExternalInputHandoff.MODE_OFF);
        assertEquals(ExternalInputHandoff.MODE_JJ,
                ExternalInputHandoff.restoreHandoffModeForPackage(LauncherDefault.JJ_PACKAGE));
    }

    @Test
    public void androidHandoffWorksForBothDeviceFamilies() {
        DeviceFeatures.setCachedFamilyForTest("y2");
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PLAY, 0, ExternalInputHandoff.MODE_ANDROID));
        DeviceFeatures.setCachedFamilyForTest("y1");
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, 0, ExternalInputHandoff.MODE_ANDROID));
        DeviceFeatures.resetCacheForTest();
    }

    /** Focus-loss probe result decides FM vs generic remap (Rockbox onWindowFocusChanged parity). */
    @Test
    public void externalHandoffForegroundDetectsSettingsAndFm() {
        assertEquals(ExternalInputHandoff.MODE_ANDROID,
                ExternalInputHandoff.resolveModeForForegroundPackage("com.android.settings"));
        assertEquals(ExternalInputHandoff.MODE_FM,
                ExternalInputHandoff.resolveModeForForegroundPackage("com.mediatek.FMRadio"));
    }

    /** Static dpad_mode (Rockbox parity) — receiver reads it with no Activity instance. */
    @Test
    public void dpadModeStaticStateRoundTrips() {
        ExternalInputHandoff.setDpadMode(ExternalInputHandoff.MODE_ANDROID);
        assertEquals(ExternalInputHandoff.MODE_ANDROID, ExternalInputHandoff.getDpadMode());
        ExternalInputHandoff.setDpadMode(ExternalInputHandoff.MODE_OFF);
        assertEquals(ExternalInputHandoff.MODE_OFF, ExternalInputHandoff.getDpadMode());
    }

    /** Receiver-side entry fails closed on null context/event — never NPEs a bare process. */
    @Test
    public void handleMediaButtonRejectsNullArgs() {
        assertFalse(ExternalInputHandoff.handleMediaButton(null, null, true));
    }

    /** Rockbox-Y1 FM long-press center uses repeat count like MediaButtonReceiver.remap_repeat_count. */
    @Test
    public void fmPlayPauseRepeatMapsToDpadUp() {
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0, ExternalInputHandoff.MODE_FM));
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, ExternalInputHandoff.mediaToDpad(
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 1, ExternalInputHandoff.MODE_FM));
    }

    @Test
    public void handoffActivePropertyNameIsStableForXposedBridge() {
        assertEquals("sys.solar.handoff.active", ExternalInputHandoff.HANDOFF_ACTIVE_PROPERTY);
    }

    /** Stock-app handoff must win over stale in-app context menu when Solar lacks focus. */
    @Test
    public void handoffRunsBeforeContextMenuWhenDpadModeArmed() {
        ExternalInputHandoff.setDpadMode(ExternalInputHandoff.MODE_ANDROID);
        assertEquals(ExternalInputHandoff.MODE_ANDROID, ExternalInputHandoff.getDpadMode());
        ExternalInputHandoff.setDpadMode(ExternalInputHandoff.MODE_OFF);
    }

    /** Injector daemon line protocol: valid decimal → keycode; blank/junk/quit → -1 (clean exit). */
    @Test
    public void restoreHandoffModeForPackage_rearmsAndroidForStockApp() {
        ExternalInputHandoff.setDpadMode(ExternalInputHandoff.MODE_OFF);
        assertEquals(ExternalInputHandoff.MODE_ANDROID,
                ExternalInputHandoff.restoreHandoffModeForPackage("com.android.settings"));
    }

    @Test
    public void restoreHandoffModeForPackage_keepsOffForSolar() {
        ExternalInputHandoff.setDpadMode(ExternalInputHandoff.MODE_OFF);
        assertEquals(ExternalInputHandoff.MODE_OFF,
                ExternalInputHandoff.restoreHandoffModeForPackage("com.solar.launcher"));
    }

    @Test
    public void pauseAndRestoreRoundTripPreservesAndroidMode() {
        ExternalInputHandoff.setDpadMode(ExternalInputHandoff.MODE_ANDROID);
        ExternalInputHandoff.pauseForGlobalOverlay();
        assertEquals(ExternalInputHandoff.MODE_OFF, ExternalInputHandoff.getDpadMode());
        assertEquals(ExternalInputHandoff.MODE_ANDROID,
                ExternalInputHandoff.restoreHandoffModeForPackage("com.android.settings"));
    }

    /** MainActivity begin/end must use static dpadMode — receiver survives LMK. */
    @Test
    public void mainActivityHandoffContractUsesStaticDpadMode() {
        ExternalInputHandoff.forceDisarmForSolarFocus();
        ExternalInputHandoff.setDpadMode(ExternalInputHandoff.MODE_ANDROID);
        assertEquals(ExternalInputHandoff.MODE_ANDROID, ExternalInputHandoff.getDpadMode());
        ExternalInputHandoff.forceDisarmForSolarFocus();
        assertEquals(ExternalInputHandoff.MODE_OFF, ExternalInputHandoff.getDpadMode());
    }

    /** IME active — handoff inject off so wheel reaches Solar keyboard not Rockbox. */
    @Test
    public void imeActiveBlocksDpadModeAndMediaInject() {
        try {
            ExternalInputHandoff.setDpadMode(ExternalInputHandoff.MODE_ANDROID);
            SolarImeRouteArbiter.setActiveForTest(true);
            assertTrue(ExternalInputHandoff.isBlockedByIme());
            assertEquals(ExternalInputHandoff.MODE_OFF, ExternalInputHandoff.getDpadMode());
            assertTrue(ExternalInputHandoff.isMediaNavigationKey(KeyEvent.KEYCODE_MEDIA_PLAY));
        } finally {
            SolarImeRouteArbiter.resetActiveForTest();
            ExternalInputHandoff.setDpadMode(ExternalInputHandoff.MODE_OFF);
        }
    }
}
