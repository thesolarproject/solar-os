package com.solar.launcher;

import org.junit.Test;

/**
 * 2026-07-08 — USB lock action vocabulary + routing policy strings (JVM-safe).
 * Avoids loading UsbStorageOverlayReceiver (static Handler needs Android Looper).
 */
public class UsbStorageOverlayRoutingTest {

    @Test
    public void lockActionConstantDistinctFromEnable() {
        if (OverlayTriggers.ACTION_SHOW_OVERLAY_USB_STORAGE.equals(
                OverlayTriggers.ACTION_SHOW_OVERLAY_USB_STORAGE_LOCK)) {
            throw new AssertionError("lock action must differ from enable prompt action");
        }
        if (!OverlayTriggers.ACTION_SHOW_OVERLAY_USB_STORAGE_LOCK
                .endsWith("SHOW_OVERLAY_USB_STORAGE_LOCK")) {
            throw new AssertionError("lock action string must end with SHOW_OVERLAY_USB_STORAGE_LOCK");
        }
        if (!OverlayTriggers.ACTION_USB_STORAGE_LOCKED.contains("USB_STORAGE_LOCKED")) {
            throw new AssertionError("suspend broadcast action missing");
        }
        if (!OverlayTriggers.ACTION_USB_STORAGE_UNLOCKED.contains("USB_STORAGE_UNLOCKED")) {
            throw new AssertionError("unlock broadcast action missing");
        }
    }

    @Test
    public void usbLockTierNameMatchesOverlayTierScheduler() {
        if (!"usb_lock".equals(OverlayTierScheduler.TIER_USB_LOCK)) {
            throw new AssertionError("tier constant drift");
        }
        if (OverlayTierScheduler.TIER_USB.equals(OverlayTierScheduler.TIER_USB_LOCK)) {
            throw new AssertionError("enable and lock tiers must differ");
        }
    }
}
