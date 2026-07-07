package com.solar.launcher;

/**
 * 2026-07-06 — Builds Bundle snapshots for companion overlay IPC.
 * Layman: packs menu row labels the helper APK can paint.
 * Technical: Phase 3 minimal power-tier snapshot; MainActivity expands in Phase 4.
 */
public final class OverlayMenuSnapshotBuilder {

    public static final String KEY_TITLE = "title";
    public static final String KEY_LABELS = "labels";
    public static final String KEY_STATES = "states";
    public static final String KEY_SESSION_ID = "session_id";
    public static final String KEY_KIND = "kind";
    public static final String KIND_POWER = "power";

    private OverlayMenuSnapshotBuilder() {}

    /** Live quick-menu rows from MainActivity when Solar is foreground. */
    public static android.os.Bundle buildPowerFromQuickItems(
            android.content.Context ctx, ThemedContextMenu.QuickItem[] items) {
        android.os.Bundle b = new android.os.Bundle();
        b.putString(KEY_KIND, KIND_POWER);
        b.putString(KEY_TITLE, ctx.getString(R.string.context_menu_title));
        if (items == null || items.length == 0) {
            return buildPowerFallback(ctx);
        }
        String[] labels = new String[items.length];
        boolean[] states = new boolean[items.length];
        for (int i = 0; i < items.length; i++) {
            labels[i] = items[i].label;
            states[i] = items[i].visible;
        }
        b.putStringArray(KEY_LABELS, labels);
        b.putBooleanArray(KEY_STATES, states);
        return b;
    }

    /** Static fallback quick menu when Solar main is busy — companion paints these rows. */
    public static android.os.Bundle buildPowerFallback(android.content.Context ctx) {
        android.os.Bundle b = new android.os.Bundle();
        b.putString(KEY_KIND, KIND_POWER);
        b.putString(KEY_TITLE, ctx.getString(R.string.context_menu_title));
        b.putStringArray(KEY_LABELS, new String[] {
                ctx.getString(R.string.context_go_to_home),
                ctx.getString(R.string.context_action_lock_screen),
                ctx.getString(R.string.context_tier_wifi),
                ctx.getString(R.string.context_tier_bluetooth),
                ctx.getString(R.string.context_quick_power),
        });
        return b;
    }
}
