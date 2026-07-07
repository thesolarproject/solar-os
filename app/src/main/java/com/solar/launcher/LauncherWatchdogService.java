package com.solar.launcher;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/**
 * 2026-07-06 — Separate :watchdog process keeps alternate HOME apps stable after crashes.
 * Layman: a quiet helper that reopens JJ or Rockbox when you chose them as your home screen.
 * Technical: START_STICKY + PreferredLauncherEnforcer poll; survives MainActivity death.
 * Reversal: remove service; restore RockboxModeStabilizer in SolarApplication bootstrap.
 */
public final class LauncherWatchdogService extends Service {

    private static volatile boolean started;
    private PreferredLauncherEnforcer enforcer;

    /** Idempotent start — safe from boot, applyHomeTarget, and SolarApplication. */
    public static void ensureStarted(Context context) {
        if (context == null) return;
        LauncherHelperClient.ensureHelperRunning(context);
        Intent intent = new Intent(context, LauncherWatchdogService.class);
        try {
            context.getApplicationContext().startService(intent);
        } catch (Exception ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        enforcer = new PreferredLauncherEnforcer(this);
        synchronized (LauncherWatchdogService.class) {
            started = true;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (enforcer != null) {
            enforcer.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (enforcer != null) {
            enforcer.stop();
        }
        synchronized (LauncherWatchdogService.class) {
            started = false;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Test hook — poll armed state. */
    static boolean isStartedForTest() {
        return started;
    }

    /** Test hook — reset singleton between cases. */
    static void resetForTest() {
        started = false;
    }
}
