package com.solar.launcher.globalcontext;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.solar.launcher.ISolarOverlayState;

/**
 * 2026-07-08 — Binds SolarOverlayStateService for live rows + action dispatch.
 * Layman: asks Solar Home what to show and runs the tapped row when Solar is alive.
 * Technical: process-lifetime warm binder; degrade to broadcast/static when binder miss.
 * Was: one-shot bind + unbind every open (up to 800ms wait before paint).
 * Now: keep binder warm; power paint may proceed before async snapshot.
 * Reversal: unbind after each fetch/dispatch; drop get()/async APIs.
 */
public final class SolarOverlayStateClient {

    private static final String SOLAR_PKG = "com.solar.launcher";
    private static final String STATE_SERVICE = SOLAR_PKG + ".SolarOverlayStateService";
    public static final String ACTION_BIND =
            "com.solar.launcher.action.BIND_OVERLAY_STATE";

    /** 2026-07-08 — One client per companion process so hot opens skip bind wait. */
    private static SolarOverlayStateClient sInstance;

    private ISolarOverlayState binder;
    private ServiceConnection connection;
    private boolean binding;

    /** Callback for background power snapshot (may run off main). */
    public interface PowerSnapshotCallback {
        /** Deliver snapshot (nullable) and binder round-trip ms. */
        void onPowerSnapshot(Bundle snap, long elapsedMs);
    }

    private SolarOverlayStateClient() {}

    /**
     * 2026-07-08 — Shared client so bind stays warm across overlay opens.
     * Layman: reuses the same phone line to Solar instead of dialing every time.
     * Technical: singleton holder; process death clears it.
     */
    public static synchronized SolarOverlayStateClient get() {
        if (sInstance == null) {
            sInstance = new SolarOverlayStateClient();
        }
        return sInstance;
    }

    /**
     * 2026-07-08 — Instant snapshot when binder already connected; never waits.
     * Layman: if we already know Solar’s menu, hand it back right now.
     * Technical: no bindSync; RemoteException → null.
     * Reversal: always call fetchPowerSnapshot (blocking).
     */
    public Bundle tryHotPowerSnapshot(final Context ctx) {
        // #region agent log
        long t0 = android.os.SystemClock.uptimeMillis();
        // #endregion
        if (ctx == null) return null;
        if (!CompanionSolarProbe.isSolarInstalled(ctx.getApplicationContext())) {
            return null;
        }
        ISolarOverlayState hot = binder;
        if (hot == null) {
            // #region agent log
            AgentDebugLog.log("H-A", "SolarOverlayStateClient.tryHotPowerSnapshot",
                    "cold", "{\"elapsedMs\":0}");
            // #endregion
            return null;
        }
        try {
            Bundle snap = hot.getPowerMenuSnapshot();
            // #region agent log
            AgentDebugLog.log("H-A", "SolarOverlayStateClient.tryHotPowerSnapshot",
                    "hot_ok", "{\"elapsedMs\":"
                            + (android.os.SystemClock.uptimeMillis() - t0)
                            + ",\"hasSnap\":" + (snap != null) + "}");
            // #endregion
            return snap;
        } catch (RemoteException e) {
            binder = null;
            // #region agent log
            AgentDebugLog.log("H-A", "SolarOverlayStateClient.tryHotPowerSnapshot",
                    "hot_fail", "{\"elapsedMs\":"
                            + (android.os.SystemClock.uptimeMillis() - t0) + "}");
            // #endregion
            return null;
        }
    }

    /**
     * 2026-07-08 — Bind + snapshot off caller thread; keep connection warm.
     * Layman: quietly asks Solar for live rows while the fallback menu is already up.
     * Technical: bg thread bindSync ≤800ms then callback; no unbind.
     * Reversal: sync fetchPowerSnapshot before paint.
     */
    public void fetchPowerSnapshotAsync(final Context ctx,
            final PowerSnapshotCallback callback) {
        if (callback == null) return;
        if (ctx == null) {
            callback.onPowerSnapshot(null, 0L);
            return;
        }
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                // #region agent log
                long t0 = android.os.SystemClock.uptimeMillis();
                // #endregion
                Bundle snap = fetchPowerSnapshotKeepWarm(app);
                // #region agent log
                long elapsed = android.os.SystemClock.uptimeMillis() - t0;
                AgentDebugLog.log("H-A", "SolarOverlayStateClient.fetchPowerSnapshotAsync",
                        "done", "{\"elapsedMs\":" + elapsed
                                + ",\"hasSnap\":" + (snap != null) + "}");
                // #endregion
                callback.onPowerSnapshot(snap, elapsed);
            }
        }, "solar-overlay-snap").start();
    }

    /**
     * 2026-07-08 — Sync snapshot that leaves the binder bound for the next open.
     * Layman: still dials if needed, but keeps the line open afterward.
     * Technical: bindSync when cold; omit unbind (was unbind in finally).
     * Reversal: restore finally { unbind(app); }.
     */
    public Bundle fetchPowerSnapshotKeepWarm(final Context app) {
        // #region agent log
        long t0 = android.os.SystemClock.uptimeMillis();
        // #endregion
        if (app == null) return null;
        if (!CompanionSolarProbe.isSolarInstalled(app)) {
            // #region agent log
            AgentDebugLog.log("H-A", "SolarOverlayStateClient.fetchPowerSnapshot",
                    "solar_not_installed", "{\"elapsedMs\":0}");
            // #endregion
            return null;
        }
        if (binder != null) {
            try {
                Bundle hot = binder.getPowerMenuSnapshot();
                // #region agent log
                AgentDebugLog.log("H-A", "SolarOverlayStateClient.fetchPowerSnapshot",
                        "hot_binder", "{\"elapsedMs\":"
                                + (android.os.SystemClock.uptimeMillis() - t0)
                                + ",\"hasSnap\":" + (hot != null) + "}");
                // #endregion
                return hot;
            } catch (RemoteException ignored) {
                binder = null;
            }
        }
        bindSync(app);
        if (binder == null) {
            // #region agent log
            AgentDebugLog.log("H-A", "SolarOverlayStateClient.fetchPowerSnapshot",
                    "bind_miss", "{\"elapsedMs\":"
                            + (android.os.SystemClock.uptimeMillis() - t0) + "}");
            // #endregion
            return null;
        }
        try {
            Bundle snap = binder.getPowerMenuSnapshot();
            // #region agent log
            AgentDebugLog.log("H-A", "SolarOverlayStateClient.fetchPowerSnapshot",
                    "bind_ok", "{\"elapsedMs\":"
                            + (android.os.SystemClock.uptimeMillis() - t0)
                            + ",\"hasSnap\":" + (snap != null) + "}");
            // #endregion
            return snap;
        } catch (RemoteException e) {
            // #region agent log
            AgentDebugLog.log("H-A", "SolarOverlayStateClient.fetchPowerSnapshot",
                    "remote_fail", "{\"elapsedMs\":"
                            + (android.os.SystemClock.uptimeMillis() - t0) + "}");
            // #endregion
            return null;
        }
        // 2026-07-08 — Intentionally no unbind: warm reuse for next power/action.
    }

    /** @deprecated Prefer tryHot + fetchPowerSnapshotAsync for paint-first path. */
    public Bundle fetchPowerSnapshot(final Context ctx) {
        if (ctx == null) return null;
        return fetchPowerSnapshotKeepWarm(ctx.getApplicationContext());
    }

    /** Fetch context/app-menu snapshot for a session id. */
    public Bundle fetchContextSnapshot(Context ctx, String sessionId) {
        if (ctx == null || sessionId == null) return null;
        final Context app = ctx.getApplicationContext();
        if (!CompanionSolarProbe.isSolarInstalled(app)) return null;
        bindSync(app);
        if (binder == null) return null;
        try {
            return binder.getContextMenuSnapshot(sessionId);
        } catch (RemoteException e) {
            return null;
        }
        // 2026-07-08 — Keep warm (was unbind); app-menu rare vs power opens.
    }

    /**
     * 2026-07-08 — Bind + dispatch row pick (power or solar_home_* / app menu).
     * Layman: tell Solar “user picked this option” without starting Solar’s own menu.
     * @return true when Solar binder accepted the action
     */
    public boolean dispatchActionBound(Context ctx, String sessionId, int index) {
        if (ctx == null || sessionId == null) return false;
        final Context app = ctx.getApplicationContext();
        if (!CompanionSolarProbe.isSolarInstalled(app)) return false;
        if (binder == null) {
            bindSync(app);
        }
        if (binder == null) return false;
        try {
            return binder.dispatchAction(sessionId, index);
        } catch (RemoteException e) {
            binder = null;
            return false;
        }
        // 2026-07-08 — Keep warm after dispatch (was unbind every pick).
    }

    /** Legacy no-bind dispatch — only works if binder already held (usually false). */
    public boolean dispatchAction(Context ctx, String sessionId, int index) {
        return dispatchActionBound(ctx, sessionId, index);
    }

    private void bindSync(final Context app) {
        if (binding || binder != null) return;
        final Object lock = new Object();
        binding = true;
        // #region agent log
        final long bindStart = android.os.SystemClock.uptimeMillis();
        // #endregion
        connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                binder = ISolarOverlayState.Stub.asInterface(service);
                // #region agent log
                AgentDebugLog.log("H-A", "SolarOverlayStateClient.onServiceConnected",
                        "connected", "{\"waitMs\":"
                                + (android.os.SystemClock.uptimeMillis() - bindStart) + "}");
                // #endregion
                synchronized (lock) {
                    lock.notifyAll();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                binder = null;
            }
        };
        Intent i = new Intent(ACTION_BIND);
        i.setComponent(new ComponentName(SOLAR_PKG, STATE_SERVICE));
        try {
            app.bindService(i, connection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            binding = false;
            // #region agent log
            AgentDebugLog.log("H-A", "SolarOverlayStateClient.bindSync",
                    "bindService_throw", "{\"err\":\""
                            + AgentDebugLogSafe(e) + "\"}");
            // #endregion
            return;
        }
        synchronized (lock) {
            try {
                lock.wait(800L);
            } catch (InterruptedException ignored) {}
        }
        // #region agent log
        AgentDebugLog.log("H-A", "SolarOverlayStateClient.bindSync",
                "wait_done", "{\"waitMs\":"
                        + (android.os.SystemClock.uptimeMillis() - bindStart)
                        + ",\"hasBinder\":" + (binder != null) + "}");
        // #endregion
        binding = false;
    }

    /** Tiny exception stringify for debug payload — avoids AgentDebugLog dependency cycles. */
    private static String AgentDebugLogSafe(Exception e) {
        if (e == null || e.getMessage() == null) return "ex";
        return e.getMessage().replace("\"", "'");
    }

    /**
     * 2026-07-08 — Explicit release (tests / process teardown); open path no longer calls this.
     * Layman: hangs up the Solar phone line on purpose.
     * Technical: unbindService + clear stubs.
     */
    void unbind(Context app) {
        if (connection != null && app != null) {
            try {
                app.unbindService(connection);
            } catch (Exception ignored) {}
        }
        connection = null;
        binder = null;
        binding = false;
    }
}
