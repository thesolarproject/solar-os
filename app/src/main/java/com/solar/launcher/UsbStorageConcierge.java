package com.solar.launcher;

import org.json.JSONObject;

/**
 * 2026-07-06 — Xposed {@code UsbStorageActivity} hook owns plug-in UX when active.
 * Layman: Solar bridge intercepts SystemUI before it paints; Java helpers only fallback if hook missed.
 *
 * <p>2026-07-17 — Never fork {@code getprop}/{@code su} on the UI thread during unplug.
 * Layman: unplugging after disk mode must not freeze the wheel for a second.
 * Reversal: delete and restore {@link Y1UsbFocusHelper} immediate USB_STATE overlay routing.
 */
public final class UsbStorageConcierge {

    /** Set by SolarContextBridge {@link com.solar.launcher.xposed.bridge.UsbStorageHooks} on intercept. */
    public static final String SYSPROP = "sys.solar.usb.concierge";
    public static final String SYSPROP_AT = "sys.solar.usb.concierge_at";
    /**
     * Wait for Xposed concierge before USB_STATE broadcast fallbacks fire (ms).
     * 2026-07-08 — Was 800ms; shortened so Solar-fg fallback feels prompt when hook misses.
     */
    private static final long FALLBACK_DELAY_MS = 250L;
    /** Cache concierge flag so hot paths never spawn getprop. */
    private static final long CONCIERGE_TTL_MS = 1000L;

    private static volatile String cachedConcierge;
    private static volatile long cachedConciergeAtMs;

    private UsbStorageConcierge() {}

    /** True when SystemUI USB activity was replaced by the bridge this host session. */
    public static boolean isXposedConciergeActive() {
        long now = System.currentTimeMillis();
        String cached = cachedConcierge;
        if (cached != null && (now - cachedConciergeAtMs) < CONCIERGE_TTL_MS) {
            return "1".equals(cached);
        }
        String v = readSyspropReflect(SYSPROP);
        cachedConcierge = v;
        cachedConciergeAtMs = now;
        return "1".equals(v);
    }

    /**
     * Cable unplug — allow next plug-in to re-arm concierge + fallbacks.
     * Safe on the main thread: reflection setprop only; no {@code su}/exec.
     */
    public static void clearOnUsbDisconnect() {
        OverlayTierScheduler.clearPendingUsbPrompt();
        cachedConcierge = "0";
        cachedConciergeAtMs = System.currentTimeMillis();
        writeSyspropFast(SYSPROP, "0");
        writeSyspropFast(SYSPROP_AT, "0");
    }

    /** USB_STATE receivers defer overlay/HOME until Xposed had a chance to run. */
    public static long fallbackDelayMs() {
        return FALLBACK_DELAY_MS;
    }

    /** Forget TTL after Xposed sets concierge=1 so next probe sees it. */
    public static void invalidateCache() {
        cachedConcierge = null;
        cachedConciergeAtMs = 0L;
    }

    private static String readSyspropReflect(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class).invoke(null, key, "");
            return v != null ? v.toString().trim() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static void writeSyspropFast(String key, String val) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            sp.getMethod("set", String.class, String.class).invoke(null, key, val);
            return;
        } catch (Throwable ignored) {}
        // Last resort: never block the UI on su during unplug.
        RootShell.runAsync("setprop " + key + " " + val);
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
