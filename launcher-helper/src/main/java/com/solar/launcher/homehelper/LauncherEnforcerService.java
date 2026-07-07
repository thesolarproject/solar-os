package com.solar.launcher.homehelper;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.solar.home.policy.HomeTargetPolicy;

/**
 * 2026-07-06 — Poll saved HOME and enforce foreground for Rockbox/JJ in helper :watchdog process.
 * Layman: if you picked Rockbox as home and it hiccups, this quietly reopens it.
 * Technical: 2.5s poll; calls solar-launcher-exec.sh enforce-foreground; skips solar target.
 * Reversal: stop service; Solar LauncherWatchdogService owns enforcement again.
 */
public final class LauncherEnforcerService extends Service implements Runnable {

    private static final String TAG = "LauncherEnforcer";
    private static final long POLL_MS = 2500L;

    private Handler handler;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handler.removeCallbacks(this);
        handler.postDelayed(this, POLL_MS);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(this);
        super.onDestroy();
    }

    @Override
    public void run() {
        try {
            maybeEnforce();
        } finally {
            handler.postDelayed(this, POLL_MS);
        }
    }

    private void maybeEnforce() {
        if (isHomeApplyInProgress()) return;
        if (com.solar.home.policy.LauncherTransitionGuard.shouldPauseHomeEnforcement()) return;
        String target = LauncherHomeActivity.readSystemProperty(
                HomeTargetPolicy.PROP_HOME_TARGET, HomeTargetPolicy.TARGET_SOLAR);
        if (HomeTargetPolicy.TARGET_SOLAR.equals(target)) return;
        if (!HomeTargetPolicy.isAlternateHomeTarget(target)) return;
        LauncherSwitchExecutor.enforceForeground();
    }

    private static boolean isHomeApplyInProgress() {
        return "1".equals(LauncherHomeActivity.readSystemProperty(
                HomeTargetPolicy.PROP_HOME_APPLYING, "0"));
    }

    /** Idempotent start — safe from boot, switch receiver, and platform daemon. */
    public static void ensureStarted(Context context) {
        if (context == null) return;
        try {
            Intent i = new Intent(context, LauncherEnforcerService.class);
            context.startService(i);
        } catch (Exception e) {
            Log.w(TAG, "ensureStarted failed: " + e.getMessage());
        }
    }
}
