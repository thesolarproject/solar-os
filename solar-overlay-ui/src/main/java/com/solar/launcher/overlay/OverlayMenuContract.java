package com.solar.launcher.overlay;

/**
 * 2026-07-08 — Stable public strings for global overlay APP_MENU / dialog / power.
 * Layman: the agreed vocabulary so Solar Home and other apps open the same system menu.
 * Technical: mirrors OverlayTriggers / CompanionOverlayActions; signatureOrSystem gate.
 * Reversal: delete; callers hard-code action strings again.
 */
public final class OverlayMenuContract {

    /** Optional manifest meta-data — voluntary OverlayMenuClient callers should set true. */
    public static final String META_OVERLAY_OPT_IN = "com.solar.launcher.overlay_opt_in";

    public static final String PERMISSION_OVERLAY_TRIGGER =
            "com.solar.launcher.permission.OVERLAY_TRIGGER";

    public static final String ACTION_SHOW_OVERLAY_POWER =
            "com.solar.launcher.action.SHOW_OVERLAY_POWER";
    public static final String ACTION_SHOW_OVERLAY_APP_MENU =
            "com.solar.launcher.action.SHOW_OVERLAY_APP_MENU";
    public static final String ACTION_SHOW_OVERLAY_NATIVE_DIALOG =
            "com.solar.launcher.action.SHOW_OVERLAY_NATIVE_DIALOG";
    public static final String ACTION_SHOW_OVERLAY_USB_STORAGE =
            "com.solar.launcher.action.SHOW_OVERLAY_USB_STORAGE";
    public static final String ACTION_SHOW_OVERLAY_USB_STORAGE_LOCK =
            "com.solar.launcher.action.SHOW_OVERLAY_USB_STORAGE_LOCK";
    public static final String ACTION_DISMISS_OVERLAY =
            "com.solar.launcher.action.DISMISS_OVERLAY";

    public static final String ACTION_APP_MENU_RESULT =
            "com.solar.launcher.action.APP_MENU_RESULT";
    public static final String ACTION_DIALOG_RESULT =
            "com.solar.launcher.action.DIALOG_RESULT";

    public static final String EXTRA_MENU_TITLES = "menu_titles";
    public static final String EXTRA_MENU_SESSION_ID = "menu_session_id";
    public static final String EXTRA_MENU_TITLE = "menu_title";
    public static final String EXTRA_MENU_CALLER_PACKAGE = "menu_caller_package";
    public static final String EXTRA_MENU_HAS_SUBMENU = "menu_has_submenu";
    public static final String EXTRA_SELECTED_INDEX = "menu_selected_index";
    public static final String EXTRA_DIALOG_MESSAGE = "dialog_message";
    public static final String EXTRA_DIALOG_BUTTONS = "dialog_buttons";
    public static final String EXTRA_USB_STORAGE_LOCK = "usb_storage_lock";

    /** Cancel / Back — no row chosen. */
    public static final int RESULT_CANCELLED = -1;

    private OverlayMenuContract() {}
}
