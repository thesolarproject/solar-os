package com.solar.launcher;

/**
 * 2026-07-06 — IPC: companion reads live overlay menu rows from Solar main process.
 * Layman: shares quick-menu data with the helper APK without opening the full Solar UI.
 * Technical: Bundle snapshots avoid heavy Parcelable churn on API 17.
 */
interface ISolarOverlayState {
    boolean isSolarAlive();
    int policyRevision();
    android.os.Bundle getPowerMenuSnapshot();
    android.os.Bundle getContextMenuSnapshot(String sessionId);
    boolean dispatchAction(String sessionId, int actionIndex);
}
