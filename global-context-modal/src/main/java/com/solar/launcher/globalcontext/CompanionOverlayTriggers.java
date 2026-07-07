package com.solar.launcher.globalcontext;

/**
 * 2026-07-05 — Intent/action constants shared with Xposed bridge (Phase 2a retarget).
 * Layman: the vocabulary companion and hooks use to open overlays and route holds.
 * Technical: mirrors {@code com.solar.launcher.OverlayTriggers} power/rescue actions.
 * Reversal: delete; bridge keeps targeting Solar OverlayTriggers only.
 */
public final class CompanionOverlayTriggers {

    /** Same permission Solar declares — platform-signed companion matches. */
    public static final String PERMISSION = "com.solar.launcher.permission.OVERLAY_TRIGGER";

    /** Y2 power-hold / Y1 BACK-long — open quick modal at power tier. */
    public static final String ACTION_SHOW_OVERLAY_POWER =
            "com.solar.launcher.action.SHOW_OVERLAY_POWER";

    /** Warm {@code :overlay} process without painting UI. */
    public static final String ACTION_OVERLAY_KEEPALIVE =
            "com.solar.launcher.action.OVERLAY_KEEPALIVE";

    /** Tear down any global overlay tier. */
    public static final String ACTION_DISMISS_OVERLAY =
            "com.solar.launcher.action.DISMISS_OVERLAY";

    /** Keep {@code :hold} rescue HUD polling alive. */
    public static final String ACTION_RESCUE_HOLD_KEEPALIVE =
            "com.solar.launcher.action.RESCUE_HOLD_KEEPALIVE";

    /** PWM / root daemon → coordinator — finger down on POWER or BACK. */
    public static final String ACTION_HOLD_DOWN =
            "com.solar.launcher.globalcontext.action.HOLD_DOWN";

    /** Finger up — disarm rescue track and close modal if applicable. */
    public static final String ACTION_HOLD_UP =
            "com.solar.launcher.globalcontext.action.HOLD_UP";

    public static final String EXTRA_KEY_CODE = "key_code";
    public static final String EXTRA_FOREGROUND_PKG = "foreground_pkg";
    public static final String EXTRA_HOLD_MS = "hold_ms";
    public static final String EXTRA_Y2_DEVICE = "y2_device";

    private CompanionOverlayTriggers() {}
}
