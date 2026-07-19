package com.solar.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * 2026-07-18 — Sticky USB_STATE host (PC) vs charger-only.
 * Layman: true when a computer cable is in — not a wall charger alone.
 * Tech: same extras as {@link Y1UsbFocusHelper} / {@link UsbHostWakeReceiver}.
 * Reversal: inline sticky extras at each Power-menu call site.
 */
public final class UsbHostPresence {

    static final String ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE";

    private UsbHostPresence() {}

    /**
     * 2026-07-18 — Live sticky USB_STATE says a USB host/PC is attached.
     * Layman: cable to a computer, not just charging.
     */
    public static boolean isUsbHostConnected(Context ctx) {
        if (ctx == null) return false;
        try {
            Intent sticky = ctx.getApplicationContext().registerReceiver(null,
                    new IntentFilter(ACTION_USB_STATE));
            return isUsbHostIntent(sticky);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 2026-07-18 — Host when OEM host flags or configured+connected (PC finished enum).
     * Wall chargers are often connected without configured → false.
     */
    public static boolean isUsbHostIntent(Intent intent) {
        if (intent == null) return false;
        boolean connected = intent.getBooleanExtra("connected", false);
        if (!connected) return false;
        boolean extraHost = intent.getBooleanExtra("host_connected", false);
        boolean extraMassStorage = intent.getBooleanExtra("mass_storage", false);
        boolean extraPcKnow = intent.getBooleanExtra("USB_IS_PC_KNOW_ME", false);
        boolean configured = intent.getBooleanExtra("configured", false);
        return extraHost || extraMassStorage || extraPcKnow || configured;
    }

    /**
     * 2026-07-18 — Power menu: show Enable USB Storage only for PC host, experiment on, not already UMS.
     * Layman: quick Turn on when a computer is plugged — hide for chargers and when already in disk mode.
     */
    public static boolean shouldOfferEnableUsbStorageInPowerMenu(Context ctx) {
        if (ctx == null) return false;
        if (!UsbMassStorageExperiment.isEnabled(ctx)) return false;
        if (!isUsbHostConnected(ctx)) return false;
        if (UsbMassStorageController.isUserSessionActive()) return false;
        if (UsbMassStorageController.isKernelMassStorageMode()) return false;
        if (UsbMassStorageController.isMassStorageExported()) return false;
        return true;
    }

    /** Test hook — host derivation without sticky broadcasts. */
    static boolean isUsbHostForTest(boolean connected, boolean host, boolean massStorage,
            boolean pcKnow, boolean configured) {
        if (!connected) return false;
        return host || massStorage || pcKnow || configured;
    }

    /** Test hook — power-row gate without live USB/UMS probes. */
    static boolean shouldOfferEnableForTest(boolean experimentOn, boolean hostConnected,
            boolean sessionActive, boolean kernelUms, boolean exported) {
        if (!experimentOn || !hostConnected) return false;
        if (sessionActive || kernelUms || exported) return false;
        return true;
    }
}
