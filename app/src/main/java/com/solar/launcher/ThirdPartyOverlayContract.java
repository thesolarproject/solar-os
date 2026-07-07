package com.solar.launcher;

/**
 * 2026-07-07 — Stable strings for third-party / hook overlay integration.
 * Layman: documents how apps participate in Solar's global overlay without custom manifest tags.
 * Technical: mirrors {@link OverlayTriggers}; hooks are primary — opt-in meta-data is reserved.
 * Reversal: delete; hooks-only with undocumented intent actions.
 */
public final class ThirdPartyOverlayContract {

    /** Optional manifest meta-data — future programmatic overlay requests. */
    public static final String META_OVERLAY_OPT_IN = "com.solar.launcher.overlay_opt_in";

    public static final String PERMISSION_OVERLAY_TRIGGER = OverlayTriggers.PERMISSION;

    public static final String ACTION_APP_MENU_RESULT = OverlayTriggers.ACTION_APP_MENU_RESULT;
    public static final String ACTION_DIALOG_RESULT = OverlayTriggers.ACTION_DIALOG_RESULT;

    public static final String EXTRA_MENU_SESSION_ID = OverlayTriggers.EXTRA_MENU_SESSION_ID;
    public static final String EXTRA_SELECTED_INDEX = OverlayTriggers.EXTRA_SELECTED_INDEX;
    public static final String EXTRA_MENU_HAS_SUBMENU = OverlayTriggers.EXTRA_MENU_HAS_SUBMENU;

    private ThirdPartyOverlayContract() {}
}
