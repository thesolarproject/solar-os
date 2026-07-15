package com.solar.launcher;

import com.solar.launcher.overlay.OverlayMenuContract;

/**
 * 2026-07-07 — Stable strings for third-party / hook overlay integration.
 * 2026-07-08 — Aliases {@link OverlayMenuContract}; prefer OverlayMenuClient for voluntary show.
 * Hooks remain primary for stock Menu / AlertDialog. Opt-in meta enables OverlayMenuClient warns.
 * Reversal: inline OverlayTriggers constants; drop OverlayMenuContract alias.
 */
public final class ThirdPartyOverlayContract {

    /** Manifest meta-data — set true for voluntary OverlayMenuClient callers. */
    public static final String META_OVERLAY_OPT_IN = OverlayMenuContract.META_OVERLAY_OPT_IN;

    public static final String PERMISSION_OVERLAY_TRIGGER =
            OverlayMenuContract.PERMISSION_OVERLAY_TRIGGER;

    public static final String ACTION_SHOW_OVERLAY_APP_MENU =
            OverlayMenuContract.ACTION_SHOW_OVERLAY_APP_MENU;
    public static final String ACTION_SHOW_OVERLAY_NATIVE_DIALOG =
            OverlayMenuContract.ACTION_SHOW_OVERLAY_NATIVE_DIALOG;
    public static final String ACTION_SHOW_OVERLAY_POWER =
            OverlayMenuContract.ACTION_SHOW_OVERLAY_POWER;
    public static final String ACTION_DISMISS_OVERLAY =
            OverlayMenuContract.ACTION_DISMISS_OVERLAY;

    public static final String ACTION_APP_MENU_RESULT = OverlayMenuContract.ACTION_APP_MENU_RESULT;
    public static final String ACTION_DIALOG_RESULT = OverlayMenuContract.ACTION_DIALOG_RESULT;

    public static final String EXTRA_MENU_SESSION_ID = OverlayMenuContract.EXTRA_MENU_SESSION_ID;
    public static final String EXTRA_SELECTED_INDEX = OverlayMenuContract.EXTRA_SELECTED_INDEX;
    public static final String EXTRA_MENU_HAS_SUBMENU = OverlayMenuContract.EXTRA_MENU_HAS_SUBMENU;
    public static final String EXTRA_MENU_TITLES = OverlayMenuContract.EXTRA_MENU_TITLES;
    public static final String EXTRA_MENU_TITLE = OverlayMenuContract.EXTRA_MENU_TITLE;
    public static final String EXTRA_MENU_CALLER_PACKAGE =
            OverlayMenuContract.EXTRA_MENU_CALLER_PACKAGE;

    private ThirdPartyOverlayContract() {}
}
