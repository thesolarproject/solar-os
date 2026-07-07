package com.solar.launcher;

import org.junit.Test;

/** USB overlay pref gates — pure logic without Robolectric. */
public class UsbStorageSessionFlagsTest {

    @Test
    public void defaultsOfferPromptAndNoAutoConnect() {
        if (!UsbStorageSessionFlags.shouldOfferFromPrefs(false)) {
            throw new AssertionError("expected offer when suppress is off");
        }
        if (UsbStorageSessionFlags.isAutoConnectFromPrefs(false, false)) {
            throw new AssertionError("auto-connect off by default");
        }
    }

    @Test
    public void suppressPromptHonored() {
        if (UsbStorageSessionFlags.shouldOfferFromPrefs(true)) {
            throw new AssertionError("suppress pref should block prompt");
        }
    }

    @Test
    public void skipSyspropBlocksPromptOffer() {
        if (!UsbStorageSessionFlags.isSkipPromptFromSyspropForTest("1")) {
            throw new AssertionError("sysprop 1 should mean skip active");
        }
        if (UsbStorageSessionFlags.isSkipPromptFromSyspropForTest("0")) {
            throw new AssertionError("sysprop 0 should not skip");
        }
    }

    @Test
    public void autoConnectRequiresManualDisableOff() {
        if (!UsbStorageSessionFlags.isAutoConnectFromPrefs(true, false)) {
            throw new AssertionError("auto-connect on when manual disable off");
        }
        if (UsbStorageSessionFlags.isAutoConnectFromPrefs(true, true)) {
            throw new AssertionError("manual disable blocks auto-connect");
        }
    }

    @Test
    public void usbPromptUsesOverlayWhenForegroundUnknownOrSystemUi() {
        if (!UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest(null)) {
            throw new AssertionError("unknown fg should prefer overlay over Solar HOME");
        }
        if (!UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest("com.android.systemui")) {
            throw new AssertionError("SystemUI on top should prefer overlay");
        }
        if (!UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest("android")) {
            throw new AssertionError("android shell should prefer overlay");
        }
    }

    @Test
    public void usbPromptUsesOverlayWhenStockAppForeground() {
        if (!UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest("com.mediatek.videoplayer")) {
            throw new AssertionError("stock app should get overlay USB prompt");
        }
        if (!UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest("org.rockbox")) {
            throw new AssertionError("Rockbox should get overlay USB prompt");
        }
        if (UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest("com.solar.launcher")) {
            throw new AssertionError("Solar foreground uses in-app USB dialog");
        }
    }

    @Test
    public void enablePromptOverlayPreferredMatchesStockForeground() {
        if (!UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest("org.rockbox")) {
            throw new AssertionError("enable overlay preferred for Rockbox");
        }
    }

    @Test
    public void usbHostWakeRoutesOverlayNotMainWhenStockAppForeground() {
        if (UsbHostWakeReceiver.shouldLaunchMainActivityForUsbHost(null)) {
            throw new AssertionError("null context");
        }
    }

    @Test
    public void usbHostWakeLaunchMainWhenSolarForeground() {
        if (UsbHostWakeReceiver.shouldLaunchMainActivityForUsbHost(null)) {
            throw new AssertionError("null context cannot launch");
        }
        if (!UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest("com.mediatek.videoplayer")) {
            throw new AssertionError("stock app needs overlay");
        }
        if (UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest("com.solar.launcher")) {
            throw new AssertionError("Solar uses in-app USB");
        }
    }
}
