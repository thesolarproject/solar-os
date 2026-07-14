package com.solar.launcher.overlay;

/**
 * 2026-07-08 — Result for OverlayMenuClient.showContextMenu.
 * Layman: runs when the user picks a row or backs out of the system options list.
 * Technical: index is 0-based; RESULT_CANCELLED on Back/dismiss.
 * Reversal: delete; callers register BroadcastReceiver for APP_MENU_RESULT manually.
 */
public interface OverlayMenuCallback {
    /** Selected row index, or {@link OverlayMenuContract#RESULT_CANCELLED}. */
    void onMenuResult(int selectedIndex);
}
