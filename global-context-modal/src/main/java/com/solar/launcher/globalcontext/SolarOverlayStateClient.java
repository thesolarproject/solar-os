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
 * 2026-07-06 — Binds SolarOverlayStateService for live menu rows when Solar is alive.
 * Layman: asks the main Solar app what to show in the quick menu.
 * Technical: degrade ladder step 1 — binder miss falls back to static companion rows.
 * Reversal: delete; companion uses bundled placeholder only.
 */
public final class SolarOverlayStateClient {

    private static final String SOLAR_PKG = "com.solar.launcher";
    private static final String STATE_SERVICE = SOLAR_PKG + ".SolarOverlayStateService";
    public static final String ACTION_BIND =
            "com.solar.launcher.action.BIND_OVERLAY_STATE";

    private ISolarOverlayState binder;
    private ServiceConnection connection;
    private boolean binding;

    /** One-shot bind — returns snapshot or null when Solar IPC unavailable. */
    public Bundle fetchPowerSnapshot(final Context ctx) {
        if (ctx == null) return null;
        final Context app = ctx.getApplicationContext();
        if (!CompanionSolarProbe.isSolarInstalled(app)) return null;
        if (binder != null) {
            try {
                return binder.getPowerMenuSnapshot();
            } catch (RemoteException ignored) {}
        }
        bindSync(app);
        if (binder == null) return null;
        try {
            return binder.getPowerMenuSnapshot();
        } catch (RemoteException e) {
            return null;
        } finally {
            unbind(app);
        }
    }

    public boolean dispatchAction(Context ctx, String sessionId, int index) {
        if (binder == null || sessionId == null) return false;
        try {
            return binder.dispatchAction(sessionId, index);
        } catch (RemoteException e) {
            return false;
        }
    }

    private void bindSync(final Context app) {
        if (binding || binder != null) return;
        final Object lock = new Object();
        binding = true;
        connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                binder = ISolarOverlayState.Stub.asInterface(service);
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
            return;
        }
        synchronized (lock) {
            try {
                lock.wait(800L);
            } catch (InterruptedException ignored) {}
        }
        binding = false;
    }

    private void unbind(Context app) {
        if (connection != null) {
            try {
                app.unbindService(connection);
            } catch (Exception ignored) {}
        }
        connection = null;
        binder = null;
    }
}
