package com.solar.launcher;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 2026-07-06 — Xposed {@code UsbStorageActivity} hook owns plug-in UX when active.
 * Layman: Solar bridge intercepts SystemUI before it paints; Java helpers only fallback if hook missed.
 * Reversal: delete and restore {@link Y1UsbFocusHelper} immediate USB_STATE overlay routing.
 */
public final class UsbStorageConcierge {

    /** Set by SolarContextBridge {@link com.solar.launcher.xposed.bridge.UsbStorageHooks} on intercept. */
    public static final String SYSPROP = "sys.solar.usb.concierge";
    /** Wait for Xposed concierge before USB_STATE broadcast fallbacks fire (ms). */
    private static final long FALLBACK_DELAY_MS = 800L;

    private UsbStorageConcierge() {}

    /** True when SystemUI USB activity was replaced by the bridge this host session. */
    public static boolean isXposedConciergeActive() {
        return "1".equals(readSysprop(SYSPROP));
    }

    /** Cable unplug — allow next plug-in to re-arm concierge + fallbacks. */
    public static void clearOnUsbDisconnect() {
        OverlayTierScheduler.clearPendingUsbPrompt();
        if (RootShell.canRun()) {
            RootShell.run("setprop " + SYSPROP + " 0");
            RootShell.run("setprop sys.solar.usb.concierge_at 0");
        }
    }

    /** USB_STATE receivers defer overlay/HOME until Xposed had a chance to run. */
    public static long fallbackDelayMs() {
        return FALLBACK_DELAY_MS;
    }

    private static String readSysprop(String key) {
        Process proc = null;
        try {
            proc = Runtime.getRuntime().exec(new String[]{"getprop", key});
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), "UTF-8"));
            String line = r.readLine();
            proc.waitFor();
            return line != null ? line.trim() : "";
        } catch (Exception ignored) {
            return "";
        } finally {
            if (proc != null) proc.destroy();
        }
    }

    /** af054e — log whether fallback tier ran or concierge already handled plug-in. */
    static void logFallbackDecision(String caller, boolean conciergeActive) {
        try {
            JSONObject d = new JSONObject();
            d.put("caller", caller);
            d.put("conciergeActive", conciergeActive);
            DebugAf054eLog.log(caller, "usb concierge fallback gate", "USB-CON", d);
        } catch (Exception ignored) {}
    }
}
