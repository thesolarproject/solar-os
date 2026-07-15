package com.solar.launcher;

import android.os.Bundle;

/**
 * 2026-07-08 — Builds Bundle snapshots for companion overlay IPC.
 * Layman: packs power-tier list labels the helper paints under the chip bar.
 * Technical: power rows only — quick bar chips are painted locally in ChipOverlayHost.
 * Was: quick-bar labels duplicated as list rows (Wi‑Fi/BT text list). Now: PowerMenuRowCatalog.
 */
public final class OverlayMenuSnapshotBuilder {

    public static final String KEY_TITLE = "title";
    public static final String KEY_LABELS = "labels";
    public static final String KEY_STATES = "states";
    public static final String KEY_SESSION_ID = "session_id";
    public static final String KEY_KIND = "kind";
    /** Parallel to labels — power row dispatch tokens from {@link PowerMenuRowCatalog}. */
    public static final String KEY_POWER_ACTIONS = "power_actions";
    public static final String KIND_POWER = "power";

    private OverlayMenuSnapshotBuilder() {}

    /** Live power-tier rows when Solar main process is alive. */
    public static Bundle buildPowerListRows(android.content.Context ctx) {
        if (ctx == null) return new Bundle();
        return PowerMenuRowCatalog.buildSnapshot(ctx);
    }

    /** Static fallback when Solar main IPC handler is not registered. */
    public static Bundle buildPowerFallback(android.content.Context ctx) {
        if (ctx == null) return new Bundle();
        return PowerMenuRowCatalog.buildFallbackSnapshot(ctx);
    }
}
