package com.solar.launcher;

import org.json.JSONObject;

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
    /**
     * 2026-07-14 — Concierge prop TTL; session-idle + watchdog used to fork getprop every tick.
     * Layman: remember whether the bridge already caught USB for a short while.
     * Reversal: set TTL to 0L to force a live read every call.
     */
    private static final long CONCIERGE_TTL_MS = 1000L;
    private static volatile long conciergeCacheAtMs = 0L;
    private static volatile boolean cachedConciergeActive = false;

    private UsbStorageConcierge() {}

    /**
     * True when SystemUI USB activity was replaced by the bridge this host session.
     * 2026-07-14 — SystemProperties + TTL; was Runtime.exec("getprop") on every idle check.
     */
    public static boolean isXposedConciergeActive() {
        // currentTimeMillis — host JVM unit tests cannot call SystemClock.
        long now = System.currentTimeMillis();
        if (conciergeCacheAtMs > 0L && (now - conciergeCacheAtMs) < CONCIERGE_TTL_MS) {
            return cachedConciergeActive;
        }
        boolean active = "1".equals(readSysprop(SYSPROP));
        cachedConciergeActive = active;
        conciergeCacheAtMs = now;
        return active;
    }

    /**
     * 2026-07-14 — Drop concierge cache (cable unplug / tests).
     * Layman: forget the last "hook handled USB" answer.
     */
    public static void invalidateConciergeCache() {
        conciergeCacheAtMs = 0L;
        cachedConciergeActive = false;
    }

    /** Cable unplug — allow next plug-in to re-arm concierge + fallbacks. */
    public static void clearOnUsbDisconnect() {
        OverlayTierScheduler.clearPendingUsbPrompt();
        // 2026-07-14 — Prefer SystemProperties.set; avoid RootShell.canRun() (runs su id) on unplug.
        // Was: if (RootShell.canRun()) setprop via su — forked su just to clear a prop.
        writeSysprop(SYSPROP, "0");
        writeSysprop("sys.solar.usb.concierge_at", "0");
        invalidateConciergeCache();
        UsbMassStorageController.invalidateProbeCache();
    }

    /** USB_STATE receivers defer overlay/HOME until Xposed had a chance to run. */
    public static long fallbackDelayMs() {
        return FALLBACK_DELAY_MS;
    }

    /**
     * 2026-07-14 — Read sysprop via SystemProperties reflection (no process spawn).
     * Was: Runtime.exec("getprop") — ~10–50ms fork on UI/watchdog paths.
     * Reversal: restore getprop exec if reflection is stripped on a future build.
     */
    private static String readSysprop(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class).invoke(null, key, "");
            return v != null ? v.toString().trim() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** Write sysprop via reflection; su setprop only if reflection set fails. */
    private static void writeSysprop(String key, String val) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            sp.getMethod("set", String.class, String.class).invoke(null, key, val);
            return;
        } catch (Throwable ignored) {}
        RootShell.runAsync("setprop " + key + " " + val);
    }

    /** Test hook — TTL freshness without Android props (2026-07-14). */
    static boolean isConciergeCacheFreshForTest(long cacheAtMs, long nowMs, long ttlMs) {
        if (ttlMs <= 0L || cacheAtMs <= 0L) return false;
        return (nowMs - cacheAtMs) < ttlMs;
    }

    /** Test hook — production concierge TTL (2026-07-14). */
    static long conciergeTtlMsForTest() {
        return CONCIERGE_TTL_MS;
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
