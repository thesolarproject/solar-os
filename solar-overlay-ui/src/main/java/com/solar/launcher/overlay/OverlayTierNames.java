package com.solar.launcher.overlay;

/**
 * 2026-07-08 — Shared overlay tier strings for companion + Solar + Xposed.
 * Layman: names which kind of system menu is on screen right now.
 * Technical: must match sys.solar.overlay.tier values in OverlayTierScheduler / bridge.
 * Reversal: delete; keep strings local to OverlayTierScheduler again.
 */
public final class OverlayTierNames {

    public static final String SYSPROP_TIER = "sys.solar.overlay.tier";
    public static final String SYSPROP_PENDING_USB = "sys.solar.overlay.pending_usb";

    public static final String TIER_NONE = "none";
    public static final String TIER_NATIVE_ERROR = "native_error";
    public static final String TIER_USB = "usb";
    /** 2026-07-08 — UMS exported lock — non-dismissible except Turn Off / cable unplug. */
    public static final String TIER_USB_LOCK = "usb_lock";
    public static final String TIER_BT = "bt";
    public static final String TIER_APP_MENU = "app_menu";
    public static final String TIER_POWER = "power";

    private OverlayTierNames() {}
}
