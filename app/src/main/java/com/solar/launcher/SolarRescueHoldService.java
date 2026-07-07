package com.solar.launcher;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * 2026-07-05 — APK fallback HUD: 3, 2, 1 in the last 3s of a 10s rescue hold.
 * Layman: paints the big numbers on screen when root {@link SolarRescueHudMain} is absent.
 * Technical: :hold process + TYPE_SYSTEM_ALERT; ROM uses solar-rescue-hud-watch.sh + HudMain.
 */
public final class SolarRescueHoldService extends Service {

    private static final long POLL_ACTIVE_MS = 100L;
    private static final long POLL_IDLE_MS = 500L;
    private static final long WATCHDOG_MS = 15000L;

    private HandlerThread workerThread;
    private Handler worker;
    private WindowManager windowManager;
    private TextView hudView;
    private volatile boolean ticking;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (worker == null) return;
            // 2026-07-05 — Stale hud_second without active deadline → force disarm (H-A).
            if (SolarRescueHoldState.hudText(SolarRescueHoldService.this) != null
                    && !SolarRescueHoldState.isHoldActive()) {
                SolarRescueHoldState.disarm();
            }
            String text = SolarRescueHoldState.hudText(SolarRescueHoldService.this);
            // #region agent log — 2026-07-05: gate snapshot I/O; was firing every 100ms tick in release.
            if (Debug434250Log.ENABLED && text != null) {
                try {
                    Debug434250Log.log("SolarRescueHoldService.poll",
                            "hud visible", "H-A", Debug434250Log.rescueSnapshot());
                } catch (Exception ignored) {}
            }
            // #endregion
            if (text != null) {
                showHud(text);
                worker.postDelayed(this, POLL_ACTIVE_MS);
            } else {
                hideHud();
                worker.postDelayed(this, POLL_IDLE_MS);
            }
        }
    };

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            ensurePollLoop();
            SolarRescueHoldHost.ensureStarted(getApplicationContext());
            if (worker != null) {
                worker.postDelayed(this, WATCHDOG_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        workerThread = new HandlerThread("SolarRescueHold");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        ensurePollLoop();
        worker.postDelayed(watchdogRunnable, WATCHDOG_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensurePollLoop();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (worker != null) {
            worker.removeCallbacks(pollRunnable);
            worker.removeCallbacks(watchdogRunnable);
        }
        hideHud();
        if (workerThread != null) {
            workerThread.quitSafely();
        }
        SolarRescueHoldHost.ensureStarted(getApplicationContext());
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        SolarRescueHoldHost.ensureStarted(getApplicationContext());
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void ensurePollLoop() {
        if (worker == null) return;
        if (ticking) {
            worker.removeCallbacks(pollRunnable);
        }
        ticking = true;
        worker.post(pollRunnable);
    }

    /** 2026-07-05 — Countdown or "Restarting" chip over any foreground app. */
    private void showHud(String text) {
        if (windowManager == null || text == null) return;
        if (hudView == null) {
            hudView = buildHudView(text);
            try {
                windowManager.addView(hudView, buildLayoutParams());
            } catch (Exception ignored) {
                hudView = null;
            }
            return;
        }
        final String label = text;
        hudView.post(new Runnable() {
            @Override
            public void run() {
                if (hudView != null) hudView.setText(label);
            }
        });
    }

    private void hideHud() {
        if (windowManager == null || hudView == null) return;
        try {
            windowManager.removeView(hudView);
        } catch (Exception ignored) {}
        hudView = null;
    }

    private TextView buildHudView(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(36f);
        tv.setPadding(dp(24), dp(14), dp(24), dp(14));
        tv.setBackgroundColor(0xDD000000);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    private WindowManager.LayoutParams buildLayoutParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(36);
        return lp;
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (v * d + 0.5f);
    }
}
