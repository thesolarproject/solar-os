package com.solar.launcher.globalcontext;

import com.solar.launcher.overlay.OverlayTierNames;

/**
 * 2026-07-08 — Companion overlay tier mutex (ANR wins over USB; else in-place takeover).
 * Layman: only one system menu on screen; sticky “app not responding” beats USB plug prompts.
 * Technical: mirrors Solar OverlayTierScheduler sysprops for Xposed USB deferral.
 * Reversal: delete; Solar OverlayTierScheduler alone publishes tier props.
 */
public final class CompanionTierScheduler {

    private static volatile String activeTier = OverlayTierNames.TIER_NONE;
    private static volatile boolean pendingUsbPrompt;

    private CompanionTierScheduler() {}

    /** Publish live tier so Xposed USB concierge can defer. */
    public static void setActiveTier(String tier) {
        activeTier = tier != null ? tier : OverlayTierNames.TIER_NONE;
        SysPropHelper.set(OverlayTierNames.SYSPROP_TIER, activeTier);
    }

    /** Full shell teardown — clear tier + pending USB. */
    public static void onOverlayTeardown() {
        activeTier = OverlayTierNames.TIER_NONE;
        pendingUsbPrompt = false;
        SysPropHelper.set(OverlayTierNames.SYSPROP_TIER, OverlayTierNames.TIER_NONE);
        SysPropHelper.set(OverlayTierNames.SYSPROP_PENDING_USB, "0");
    }

    public static boolean isNativeErrorTierActive() {
        return OverlayTierNames.TIER_NATIVE_ERROR.equals(activeTier)
                || OverlayTierNames.TIER_NATIVE_ERROR.equals(
                        SysPropHelper.get(OverlayTierNames.SYSPROP_TIER, OverlayTierNames.TIER_NONE));
    }

    public static boolean shouldDeferUsbSpawn() {
        return isNativeErrorTierActive();
    }

    public static void queuePendingUsbPrompt() {
        pendingUsbPrompt = true;
        SysPropHelper.set(OverlayTierNames.SYSPROP_PENDING_USB, "1");
    }

    public static void clearPendingUsbPrompt() {
        pendingUsbPrompt = false;
        SysPropHelper.set(OverlayTierNames.SYSPROP_PENDING_USB, "0");
    }

    public static boolean hasPendingUsbPrompt() {
        return pendingUsbPrompt
                || "1".equals(SysPropHelper.get(OverlayTierNames.SYSPROP_PENDING_USB, "0"));
    }

    /** After native dialog dismiss — true when USB was queued and should paint next. */
    public static boolean tryConsumePendingUsbPrompt() {
        if (!hasPendingUsbPrompt()) return false;
        pendingUsbPrompt = false;
        SysPropHelper.set(OverlayTierNames.SYSPROP_PENDING_USB, "0");
        return true;
    }

    public static String getActiveTier() {
        return activeTier;
    }

    /** Test hook — reset volatile state between JVM cases. */
    static void resetForTest() {
        activeTier = OverlayTierNames.TIER_NONE;
        pendingUsbPrompt = false;
    }
}
