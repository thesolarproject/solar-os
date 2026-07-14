package com.solar.launcher.overlay;

/**
 * 2026-07-08 — Companion/app callbacks for chip shell side effects.
 * Layman: home/sleep/reboot/now-playing live outside the paint library.
 * Technical: ChipOverlayHost never imports Solar MainActivity / PowerActions.
 * Reversal: inline root cmds in ChipOverlayHost (ties library to companion).
 */
public interface ChipHostActions {

    /** Open preferred HOME (Solar / Rockbox / stock). */
    void launchHome();

    /** Put display to sleep (Y1 Lock chip). */
    void screenSleep();

    /** Soft reboot via root. */
    void restartDevice();

    /** Power off via root. */
    void shutdownDevice();

    /** Jump Solar Now Playing (optional binder may hide chip when idle). */
    void openNowPlaying();

    /**
     * 2026-07-08 — Optional: apply HOME switch row from Power list (Solar binder index).
     * Index maps to host Power snapshot rows when provided; -1 = unused.
     */
    void dispatchPowerRow(int index);

    /**
     * 2026-07-08 — APP_MENU pick — binder first, then broadcast.
     * @return true when caller should keep overlay open (submenu / solar_home).
     */
    boolean onAppMenuSelected(String sessionId, String callerPackage, int index,
            boolean opensSubmenu);

    /**
     * 2026-07-08 — Dispatch click/selection from native dialog/USB tiers.
     */
    void dispatchDialogSelection(String tier, int index);

    /** Tear down WM shell after chip dismiss / cancel. */
    void dismissOverlay(boolean stopService);
}
