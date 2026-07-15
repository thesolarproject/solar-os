package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.solar.launcher.theme.ThemeManager;

/**
 * 2026-07-08 — Companion/overlay USB lock → main process suspend/resume without starting Home.
 * Layman: when disk mode locks the screen, pause music if Solar is already running.
 * Technical: ACTION_USB_STORAGE_LOCKED / UNLOCKED; no-op when MainActivity not alive.
 * Reversal: delete; MainActivity only heard EXTRA_USB_OVERLAY_LOCK via startActivity.
 */
public final class UsbStorageLockSuspendReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        if (OverlayTriggers.ACTION_USB_STORAGE_LOCKED.equals(action)) {
            onLocked(context);
            return;
        }
        if (OverlayTriggers.ACTION_USB_STORAGE_UNLOCKED.equals(action)) {
            boolean forceOff = intent.getBooleanExtra(OverlayTriggers.EXTRA_USB_FORCE_OFF, false);
            onUnlocked(context, forceOff);
        }
    }

    /** UMS lock painted — prepare theme cache + pause players if activity exists. */
    private static void onLocked(Context context) {
        ThemeManager.prepareThemeForUsbStorage(context.getApplicationContext());
        // 2026-07-08 — peek only; never start MainActivity for lock suspension.
        MainActivity activity = MainActivity.peekForOverlay();
        if (activity != null) {
            activity.onExternalUsbStorageLock();
        }
    }

    /**
     * Turn Off or unplug — clear SD theme block + MainActivity lock flags if alive.
     * forceOff=true (user Turn Off while plugged) → session_dismissed until next unplug.
     * forceOff=false (cable unplug) → session reset by UsbHostSessionPolicy.onUsbHostDisconnected.
     */
    private static void onUnlocked(Context context, boolean forceOff) {
        ThemeManager.setBlockSdcardThemeAssets(false);
        if (forceOff) {
            UsbHostSessionPolicy.markUserDismissed(context.getApplicationContext());
        }
        MainActivity activity = MainActivity.peekForOverlay();
        if (activity != null) {
            activity.onExternalUsbStorageUnlock();
        }
    }
}
