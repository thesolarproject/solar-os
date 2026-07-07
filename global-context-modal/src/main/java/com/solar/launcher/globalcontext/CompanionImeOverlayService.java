package com.solar.launcher.globalcontext;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * 2026-07-06 — Companion-hosted credential IME shell (Wi-Fi/BT/search keyboards).
 * Layman: type passwords in the global menu even if Solar was force-stopped.
 * Technical: Phase 3b stub — full keyboard paint delegates to Solar :overlay when alive.
 * Reversal: delete; credential tiers stay in SolarOverlayService only.
 */
public final class CompanionImeOverlayService extends Service {

    private static final String TAG = "CompanionImeOverlay";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "IME overlay keepalive — credential host reserved for Phase 3b");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
