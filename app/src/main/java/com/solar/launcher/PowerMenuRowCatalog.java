package com.solar.launcher;

import android.content.Context;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/**
 * 2026-07-10 — Power-tier list rows for companion global context overlay IPC.
 * Layman: list under the chip bar is Restart and Shutdown only — Solar-only product.
 * Technical: labels + action tokens; quick bar stays local in ChipOverlayHost.
 */
public final class PowerMenuRowCatalog {

    private static final String ACTION_RESTART = "restart";
    private static final String ACTION_SHUTDOWN = "shutdown";
    private PowerMenuRowCatalog() {}

    /** One power list row — label for paint, token for dispatch. */
    private static final class Row {
        final String label;
        final String actionToken;

        Row(String label, String actionToken) {
            this.label = label;
            this.actionToken = actionToken;
        }
    }

    /** Bundle for SolarOverlayStateService / companion morphPowerRows. */
    public static Bundle buildSnapshot(Context ctx) {
        Bundle b = new Bundle();
        b.putString(OverlayMenuSnapshotBuilder.KEY_KIND, OverlayMenuSnapshotBuilder.KIND_POWER);
        b.putString(OverlayMenuSnapshotBuilder.KEY_TITLE,
                ctx.getString(R.string.context_quick_power));
        b.putString(OverlayMenuSnapshotBuilder.KEY_SESSION_ID, OverlayMenuSnapshotBuilder.KIND_POWER);
        List<Row> rows = buildRows(ctx);
        String[] labels = new String[rows.size()];
        String[] tokens = new String[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            labels[i] = rows.get(i).label;
            tokens[i] = rows.get(i).actionToken;
        }
        b.putStringArray(OverlayMenuSnapshotBuilder.KEY_LABELS, labels);
        b.putStringArray(OverlayMenuSnapshotBuilder.KEY_POWER_ACTIONS, tokens);
        return b;
    }

    /** Minimal fallback when Solar main is dead — restart + shutdown only. */
    public static Bundle buildFallbackSnapshot(Context ctx) {
        Bundle b = new Bundle();
        b.putString(OverlayMenuSnapshotBuilder.KEY_KIND, OverlayMenuSnapshotBuilder.KIND_POWER);
        b.putString(OverlayMenuSnapshotBuilder.KEY_TITLE,
                ctx.getString(R.string.context_quick_power));
        b.putString(OverlayMenuSnapshotBuilder.KEY_SESSION_ID, OverlayMenuSnapshotBuilder.KIND_POWER);
        b.putStringArray(OverlayMenuSnapshotBuilder.KEY_LABELS, new String[] {
                ctx.getString(R.string.context_restart_confirm),
                ctx.getString(R.string.context_shutdown_confirm),
        });
        b.putStringArray(OverlayMenuSnapshotBuilder.KEY_POWER_ACTIONS, new String[] {
                ACTION_RESTART, ACTION_SHUTDOWN,
        });
        return b;
    }

    /** Run the power list row the user picked on the companion overlay. */
    public static boolean dispatch(Context ctx, int index) {
        if (ctx == null || index < 0) return false;
        List<Row> rows = buildRows(ctx);
        if (index >= rows.size()) return false;
        String token = rows.get(index).actionToken;
        if (ACTION_RESTART.equals(token)) {
            PowerActions.restart(ctx);
            return true;
        }
        if (ACTION_SHUTDOWN.equals(token)) {
            PowerActions.shutdown(ctx);
            return true;
        }
        return false;
    }

    private static List<Row> buildRows(Context ctx) {
        ArrayList<Row> rows = new ArrayList<Row>();
        rows.add(new Row(ctx.getString(R.string.context_restart_confirm), ACTION_RESTART));
        rows.add(new Row(ctx.getString(R.string.context_shutdown_confirm), ACTION_SHUTDOWN));
        return rows;
    }

    /** Unit-test hook — row count without Android Context. */
    static int powerRowCountForTest() {
        return 2;
    }

    /** Unit-test hook — dispatch tokens without string resources. */
    static String[] powerActionTokensForTest() {
        return new String[] { ACTION_RESTART, ACTION_SHUTDOWN };
    }
}
