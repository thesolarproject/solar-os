package com.solar.launcher.overlay;

/**
 * 2026-07-08 — Result for OverlayMenuClient.showConfirmDialog.
 * Layman: runs when the user taps a button on a system confirm dialog.
 * Technical: button index matches EXTRA_DIALOG_BUTTONS order; cancel = RESULT_CANCELLED.
 * Reversal: delete; callers listen for DIALOG_RESULT broadcasts.
 */
public interface OverlayDialogCallback {
    /** Button index, or {@link OverlayMenuContract#RESULT_CANCELLED}. */
    void onDialogResult(int selectedIndex);
}
