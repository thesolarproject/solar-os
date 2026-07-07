package com.solar.launcher.radio.fm;

import android.os.Handler;
import android.os.Looper;

/**
 * Polls MTK FM RDS while Now Playing is active — updates UI when PS/RT change.
 * 2026-07-06 — background poll every 2s; main-thread callback only on delta.
 * 2026-07-06 — idempotent start: refreshPlayerUi must not reset RDS cache.
 */
public final class FmRdsPoller {
    public interface Listener {
        void onRdsChanged(String ps, String rt);
    }

    private static final long POLL_MS = 2000L;

    private final FmEngine engine;
    private final Handler mainHandler;
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            new Thread(
                    new Runnable() {
                        @Override
                        public void run() {
                            final String ps = engine.getRdsPs();
                            final String rt = engine.getRdsRt();
                            mainHandler.post(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            if (!running) return;
                                            if (!stringsEqual(ps, lastPs) || !stringsEqual(rt, lastRt)) {
                                                lastPs = ps;
                                                lastRt = rt;
                                                if (listener != null) listener.onRdsChanged(ps, rt);
                                            }
                                        }
                                    });
                        }
                    },
                    "FmRdsPoll")
                    .start();
            mainHandler.postDelayed(pollRunnable, POLL_MS);
        }
    };

    private Listener listener;
    private boolean running;
    private String lastPs;
    private String lastRt;

    public FmRdsPoller(FmEngine engine) {
        this.engine = engine;
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** True while background RDS polls are scheduled. */
    public synchronized boolean isRunning() {
        return running;
    }

    /** Begin polling; safe to call again — does not clear cached PS/RT when already running. */
    public synchronized void start() {
        if (running) return;
        running = true;
        lastPs = null;
        lastRt = null;
        mainHandler.removeCallbacks(pollRunnable);
        mainHandler.post(pollRunnable);
    }

    /** Stop background polls when FM NP exits. */
    public synchronized void stop() {
        running = false;
        mainHandler.removeCallbacks(pollRunnable);
        lastPs = null;
        lastRt = null;
    }

    /** Prime cache from chip without starting the poll loop (after tune on a worker thread). */
    public synchronized void primeCache(String ps, String rt) {
        lastPs = ps;
        lastRt = rt;
    }

    /** Force next poll to emit even if PS/RT unchanged after station tune. 2026-07-06 */
    public synchronized void invalidateCache() {
        lastPs = null;
        lastRt = null;
    }

    private static boolean stringsEqual(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
