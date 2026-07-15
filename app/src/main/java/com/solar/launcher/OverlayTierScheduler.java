package com.solar.launcher;

import android.content.Context;

/**
 * 2026-07-06 — Minimal overlay tier mutex: ANR/crash native dialog blocks USB spawn; USB shows after dismiss.
 * Layman: "app not responding" always wins over USB plug-in prompts; USB waits its turn in the same modal.
 * Tech: sysprop tier + pending_usb queue; Xposed reads props without app classpath.
 * Reversal: delete and restore parallel USB/ANR overlay startService races.
 */
public final class OverlayTierScheduler {

    /** Read by Xposed {@code SolarOverlayClient} — must stay in sync with bridge copy. */
    public static final String SYSPROP_TIER = "sys.solar.overlay.tier";
    /** 1 while USB enable prompt is queued behind native error tier. */
    public static final String SYSPROP_PENDING_USB = "sys.solar.overlay.pending_usb";

    public static final String TIER_NONE = "none";
    /** ANR / crash / stock AlertDialog replacement — highest interactive tier. */
    public static final String TIER_NATIVE_ERROR = "native_error";
    public static final String TIER_USB = "usb";
    /** 2026-07-08 — UMS already exported — non-dismissible lock (companion primary). */
    public static final String TIER_USB_LOCK = "usb_lock";
    public static final String TIER_BT = "bt";
    public static final String TIER_APP_MENU = "app_menu";
    public static final String TIER_POWER = "power";

    private static volatile String activeTier = TIER_NONE;
    private static volatile boolean pendingUsbPrompt;

    private OverlayTierScheduler() {}

    /** Publish live tier — :overlay paints and Xposed USB concierge both read sysprop. */
    public static void setActiveTier(String tier) {
        activeTier = tier != null ? tier : TIER_NONE;
        writeSysprop(SYSPROP_TIER, activeTier);
    }

    /** Clear tier on full overlay teardown — handoff/IME resume follows {@link OverlayKeyGate#disarm}. */
    public static void onOverlayTeardown() {
        activeTier = TIER_NONE;
        pendingUsbPrompt = false;
        writeSysprop(SYSPROP_TIER, TIER_NONE);
        writeSysprop(SYSPROP_PENDING_USB, "0");
    }

    /** True while native ANR/crash tier owns the global modal. */
    public static boolean isNativeErrorTierActive() {
        return TIER_NATIVE_ERROR.equals(activeTier)
                || TIER_NATIVE_ERROR.equals(readSysprop(SYSPROP_TIER, TIER_NONE));
    }

    /** USB spawn must wait — native error tier is visible or reserved (optimistic Xposed set). */
    public static boolean shouldDeferUsbSpawn() {
        return isNativeErrorTierActive();
    }

    /** Test hook — tier string without sysprop I/O. */
    static boolean shouldDeferUsbForTier(String tier) {
        return TIER_NATIVE_ERROR.equals(tier);
    }

    /** Queue USB behind native dialog — same cable session, no duplicate modals. */
    public static void queuePendingUsbPrompt() {
        pendingUsbPrompt = true;
        writeSysprop(SYSPROP_PENDING_USB, "1");
    }

    /** Cable unplug or policy block — drop stale USB queue slot. */
    public static void clearPendingUsbPrompt() {
        pendingUsbPrompt = false;
        writeSysprop(SYSPROP_PENDING_USB, "0");
    }

    /** True when USB was deferred and not yet painted after native dismiss. */
    public static boolean hasPendingUsbPrompt() {
        return pendingUsbPrompt || "1".equals(readSysprop(SYSPROP_PENDING_USB, "0"));
    }

    /**
     * Native dialog row picked or BACK cancel — consume queue when host+cable policy still allow prompt.
     * @return true when USB tier was painted in-place (caller must not tear down overlay)
     */
    public static boolean tryConsumePendingUsbPrompt(Context context) {
        if (!hasPendingUsbPrompt()) return false;
        if (context == null) {
            clearPendingUsbPrompt();
            return false;
        }
        Context app = context.getApplicationContext();
        if (!UsbHostSessionPolicy.isPcHostConnected(app)) {
            clearPendingUsbPrompt();
            return false;
        }
        if (UsbHostSessionPolicy.hasUserDismissedThisSession(app)) {
            clearPendingUsbPrompt();
            return false;
        }
        if (!UsbStorageSessionFlags.shouldOfferUsbConnectPromptAfterBootSettle(app)) {
            clearPendingUsbPrompt();
            return false;
        }
        if (UsbStorageSessionFlags.isAutoConnectEnabled(app)) {
            clearPendingUsbPrompt();
            return false;
        }
        pendingUsbPrompt = false;
        writeSysprop(SYSPROP_PENDING_USB, "0");
        return true;
    }

    /** Xposed reserves native tier before :overlay paints — blocks USB race on cold start. */
    public static void reserveNativeErrorTierEarly() {
        setActiveTier(TIER_NATIVE_ERROR);
    }

    /** Test hook — reset volatile state between JVM cases. */
    static void resetForTest() {
        activeTier = TIER_NONE;
        pendingUsbPrompt = false;
    }

    static void writeSysprop(String key, String value) {
        OverlayKeyGate.writeProperty(key, value);
    }

    private static String readSysprop(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = sp.getMethod("get", String.class, String.class);
            Object v = get.invoke(null, key, def);
            return v != null ? v.toString() : def;
        } catch (Exception e) {
            return def;
        }
    }
}
