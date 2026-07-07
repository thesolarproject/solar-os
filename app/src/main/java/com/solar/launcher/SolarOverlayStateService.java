package com.solar.launcher;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import com.solar.input.policy.GlobalInputPolicy;

/**
 * 2026-07-06 — IPC binder so companion overlay can read live menu rows when Solar is alive.
 * Layman: shares quick-menu data with the helper APK without starting the full Solar UI.
 * Technical: Phase 3 — Bundle snapshots + action dispatch back to Solar handlers.
 * Reversal: delete service; companion uses bundled placeholder rows only.
 */
public final class SolarOverlayStateService extends Service {

    public static final String ACTION_BIND_OVERLAY_STATE =
            "com.solar.launcher.action.BIND_OVERLAY_STATE";

    /** Session callbacks registered by MainActivity for in-Solar context menus. */
    private static volatile OverlayActionHandler sActionHandler;

    public interface OverlayActionHandler {
        boolean dispatchAction(String sessionId, int actionIndex);
        Bundle buildContextSnapshot(String sessionId);
        /** Live power-tier rows — null when MainActivity not ready. */
        Bundle buildPowerMenuSnapshot();
    }

    public static void registerActionHandler(OverlayActionHandler handler) {
        sActionHandler = handler;
    }

    private final ISolarOverlayState.Stub binder = new ISolarOverlayState.Stub() {
        @Override
        public boolean isSolarAlive() {
            return true;
        }

        @Override
        public int policyRevision() {
            return GlobalInputPolicy.POLICY_REV;
        }

        @Override
        public Bundle getPowerMenuSnapshot() {
            OverlayActionHandler h = sActionHandler;
            if (h != null) {
                Bundle live = h.buildPowerMenuSnapshot();
                if (live != null) return live;
            }
            return OverlayMenuSnapshotBuilder.buildPowerFallback(
                    SolarOverlayStateService.this);
        }

        @Override
        public Bundle getContextMenuSnapshot(String sessionId) {
            OverlayActionHandler h = sActionHandler;
            if (h != null && sessionId != null) {
                Bundle b = h.buildContextSnapshot(sessionId);
                if (b != null) return b;
            }
            Bundle empty = new Bundle();
            empty.putString(OverlayMenuSnapshotBuilder.KEY_SESSION_ID, sessionId);
            return empty;
        }

        @Override
        public boolean dispatchAction(String sessionId, int actionIndex) {
            OverlayActionHandler h = sActionHandler;
            if (h == null || sessionId == null) return false;
            try {
                return h.dispatchAction(sessionId, actionIndex);
            } catch (Exception e) {
                return false;
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
}
