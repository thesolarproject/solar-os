package com.solar.launcher;

import org.junit.Test;

/**
 * 2026-07-10 — Solar MainActivity owns USB enable + lock (July-2 monlith restore).
 * Layman: themed dialog for Turn on; STATE_USB_STORAGE eject screen until Turn Off.
 */
public class UsbStorageLockRoutingTest {

    /** Enable prompt never uses companion/global overlay shell. */
    @Test
    public void enablePromptNeverUsesGlobalOverlay() {
        if (UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest("org.rockbox")) {
            throw new AssertionError("no global USB overlay (Solar owns UX)");
        }
        if (UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest("com.solar.launcher")) {
            throw new AssertionError("Solar fg must own USB UX");
        }
        if (UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest(
                "com.android.settings")) {
            throw new AssertionError("stock fg also routes to Solar only");
        }
    }

    /** Lock action string must differ from enable prompt. */
    @Test
    public void lockActionDistinctFromEnablePrompt() {
        String enable = OverlayTriggers.ACTION_SHOW_OVERLAY_USB_STORAGE;
        String lock = OverlayTriggers.ACTION_SHOW_OVERLAY_USB_STORAGE_LOCK;
        if (enable == null || lock == null) {
            throw new AssertionError("USB overlay actions must be defined");
        }
        if (enable.equals(lock)) {
            throw new AssertionError("lock action must not equal enable prompt action");
        }
        if (!lock.endsWith("USB_STORAGE_LOCK")) {
            throw new AssertionError("lock action suffix mismatch: " + lock);
        }
    }

    /** Suspend broadcasts must never equal overlay show actions. */
    @Test
    public void suspendBroadcastsAreNotOverlayShowActions() {
        String locked = OverlayTriggers.ACTION_USB_STORAGE_LOCKED;
        String unlocked = OverlayTriggers.ACTION_USB_STORAGE_UNLOCKED;
        if (locked.equals(OverlayTriggers.ACTION_SHOW_OVERLAY_USB_STORAGE_LOCK)) {
            throw new AssertionError("LOCKED must not collide with SHOW lock action");
        }
        if (unlocked.equals(OverlayTriggers.ACTION_DISMISS_OVERLAY)) {
            throw new AssertionError("UNLOCKED must not collide with DISMISS");
        }
    }

    /** Tier name for lock must be published for Xposed/companion parity. */
    @Test
    public void usbLockTierNamePublished() {
        if (!"usb_lock".equals(OverlayTierScheduler.TIER_USB_LOCK)) {
            throw new AssertionError("TIER_USB_LOCK must be usb_lock");
        }
        if (OverlayTierScheduler.TIER_USB.equals(OverlayTierScheduler.TIER_USB_LOCK)) {
            throw new AssertionError("enable and lock tiers must differ");
        }
    }

    /** Lock only when mass storage is actually exported (no incomplete pre-paint). */
    @Test
    public void lockShellOnlyWhenExported() {
        if (UsbStorageOverlayReceiver.shouldPaintUsbLockShellForTest(false, false)) {
            throw new AssertionError("must not paint lock before export");
        }
        if (!UsbStorageOverlayReceiver.shouldPaintUsbLockShellForTest(false, true)) {
            throw new AssertionError("must paint lock when exported");
        }
    }

    /**
     * Unplug path must dismiss leftover shells and unlock main.
     * Source scan — JVM-safe.
     */
    @Test
    public void unplugDismissesBothHostsAndNotifiesUnlock() throws Exception {
        String wake = readRepoFile("app/src/main/java/com/solar/launcher/UsbHostWakeReceiver.java");
        if (!wake.contains("dismissGlobalOverlayIfActive")) {
            throw new AssertionError("disconnect must call dismissGlobalOverlayIfActive");
        }
        if (!wake.contains("onUsbHostDisconnected")) {
            throw new AssertionError("disconnect must clear session via onUsbHostDisconnected");
        }
        String dismiss = readRepoFile(
                "app/src/main/java/com/solar/launcher/UsbStorageOverlayReceiver.java");
        if (!dismiss.contains("COMPANION_OVERLAY_SERVICE")) {
            throw new AssertionError("unplug must dismiss companion overlay host");
        }
        if (!dismiss.contains("SOLAR_OVERLAY_SERVICE")) {
            throw new AssertionError("unplug must dismiss Solar :overlay fail-open host");
        }
        if (!dismiss.contains("notifyMainUsbUnlocked(context, false)")) {
            throw new AssertionError("unplug unlock must use forceOff=false");
        }
        if (!dismiss.contains("routeToSolar") && !dismiss.contains("launchSolarUsbHandoff")) {
            throw new AssertionError("USB router must hand off to Solar MainActivity");
        }
        if (dismiss.contains("OverlayMenuClient.showUsbStorage")) {
            throw new AssertionError("must not route USB UI to companion OverlayMenuClient");
        }
        String suspend = readRepoFile(
                "app/src/main/java/com/solar/launcher/UsbStorageLockSuspendReceiver.java");
        if (!suspend.contains("setBlockSdcardThemeAssets(false)")) {
            throw new AssertionError("unlock must clear theme SD block");
        }
        if (!suspend.contains("peekForOverlay()")) {
            throw new AssertionError("unlock must peek MainActivity — never start Home");
        }
        String main = readRepoFile("app/src/main/java/com/solar/launcher/MainActivity.java");
        if (!main.contains("enterUsbMassStorageLock()")) {
            throw new AssertionError("MainActivity must own enterUsbMassStorageLock");
        }
        if (!main.contains("showUsbMassStorageDialog()")) {
            throw new AssertionError("MainActivity must own showUsbMassStorageDialog");
        }
    }

    private static String readRepoFile(String rel) throws Exception {
        java.io.File root = new java.io.File(System.getProperty("user.dir"));
        java.io.File f = new java.io.File(root, rel);
        if (!f.isFile()) {
            f = new java.io.File(root.getParentFile(), rel);
        }
        return new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
    }
}
