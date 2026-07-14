package com.solar.launcher.globalcontext;

/**
 * 2026-07-08 — Intent vocabulary for the one companion overlay shell (all tiers).
 * Layman: every system menu kind the helper APK can show — power, USB, ANR, app menus.
 * Technical: extends CompanionOverlayTriggers; mirrors Solar OverlayTriggers action strings.
 * Reversal: shrink back to POWER-only; Solar :overlay paints other tiers again.
 */
public final class CompanionOverlayActions {

    public static final String ACTION_SHOW_OVERLAY_APP_MENU =
            "com.solar.launcher.action.SHOW_OVERLAY_APP_MENU";
    public static final String ACTION_SHOW_OVERLAY_NATIVE_DIALOG =
            "com.solar.launcher.action.SHOW_OVERLAY_NATIVE_DIALOG";
    public static final String ACTION_SHOW_OVERLAY_USB_STORAGE =
            "com.solar.launcher.action.SHOW_OVERLAY_USB_STORAGE";
    /** 2026-07-08 — Explicit lock tier action (also via EXTRA_USB_STORAGE_LOCK on USB_STORAGE). */
    public static final String ACTION_SHOW_OVERLAY_USB_STORAGE_LOCK =
            "com.solar.launcher.action.SHOW_OVERLAY_USB_STORAGE_LOCK";
    /** Companion → Solar main: UMS lock painted — suspend playback if MainActivity alive. */
    public static final String ACTION_USB_STORAGE_LOCKED =
            "com.solar.launcher.action.USB_STORAGE_LOCKED";
    /** Companion → Solar main: lock dismissed after Turn Off / unplug. */
    public static final String ACTION_USB_STORAGE_UNLOCKED =
            "com.solar.launcher.action.USB_STORAGE_UNLOCKED";
    /**
     * 2026-07-08 — True when user chose Turn Off while cable may still be plugged.
     * Layman: stay quiet until next unplug+plug; do not set on cable-unplug alone.
     */
    public static final String EXTRA_USB_FORCE_OFF = "usb_force_off";
    public static final String ACTION_SHOW_OVERLAY_BT_PAIRING =
            "com.solar.launcher.action.SHOW_OVERLAY_BT_PAIRING";
    public static final String ACTION_SHOW_OVERLAY_VOLUME =
            "com.solar.launcher.action.SHOW_OVERLAY_VOLUME";
    public static final String ACTION_SHOW_OVERLAY_TOAST =
            "com.solar.launcher.action.SHOW_OVERLAY_TOAST";
    public static final String ACTION_SHOW_OVERLAY_LAUNCHER_PICKER =
            "com.solar.launcher.action.SHOW_OVERLAY_LAUNCHER_PICKER";
    public static final String ACTION_SHOW_OVERLAY_LAUNCHER_RECOVERY =
            "com.solar.launcher.action.SHOW_OVERLAY_LAUNCHER_RECOVERY";
    public static final String ACTION_OVERLAY_KEY =
            "com.solar.launcher.action.OVERLAY_KEY";
    public static final String ACTION_APP_MENU_RESULT =
            "com.solar.launcher.action.APP_MENU_RESULT";
    public static final String ACTION_DIALOG_RESULT =
            "com.solar.launcher.action.DIALOG_RESULT";

    public static final String EXTRA_MENU_TITLES = "menu_titles";
    public static final String EXTRA_MENU_TITLE = "menu_title";
    public static final String EXTRA_MENU_SESSION_ID = "menu_session_id";
    public static final String EXTRA_MENU_CALLER_PACKAGE = "menu_caller_package";
    public static final String EXTRA_MENU_HAS_SUBMENU = "menu_has_submenu";
    public static final String EXTRA_SELECTED_INDEX = "menu_selected_index";
    public static final String EXTRA_DIALOG_MESSAGE = "dialog_message";
    public static final String EXTRA_DIALOG_BUTTONS = "dialog_buttons";
    public static final String EXTRA_USB_STORAGE_LOCK = "usb_storage_lock";
    public static final String EXTRA_KEY_CODE = "overlay_key_code";
    public static final String EXTRA_KEY_ACTION = "overlay_key_action";
    public static final String EXTRA_TOAST_TEXT = "toast_text";
    public static final String EXTRA_TOAST_DURATION_MS = "toast_duration_ms";
    public static final String EXTRA_RECOVERY_PROCESS = "recovery_process";
    public static final String EXTRA_BT_PAIRING_MODE = "bt_pairing_mode";
    public static final String EXTRA_BT_PAIRING_ADDRESS = "bt_pairing_address";
    public static final String EXTRA_BT_PAIRING_NAME = "bt_pairing_name";
    public static final String EXTRA_BT_PAIRING_PASSKEY = "bt_pairing_passkey";
    public static final String EXTRA_BT_PAIRING_PIN_PREFILL = "bt_pairing_pin_prefill";

    private CompanionOverlayActions() {}
}
