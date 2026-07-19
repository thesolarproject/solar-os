package com.solar.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

/**
 * Background writer for {@link PlayQueueStore} — keeps JSON I/O off the UI thread.
 * Layman: queue saves happen in the background so scrolling stays smooth.
 * Technical: lazy HandlerThread; debounce coalesces bursts; epoch bumps on each schedule.
 * Was: PlayQueueStore.save on UI thread. Reversal: call save() directly again.
 * 2026-07-19
 */
public final class AsyncPlayQueueWriter {
    private static final long DEBOUNCE_MS = 400L;
    private static HandlerThread thread;
    private static Handler handler;
    private static volatile long memoryEpoch;
    private static volatile long diskEpoch;
    private static volatile Context appCtx;
    private static volatile PlayQueue pending;
    private static volatile int pendingSeekMs = -1;
    private static volatile boolean pendingPlaying;
    private static final Object LOCK = new Object();

    private static final Runnable FLUSH = new Runnable() {
        @Override
        public void run() {
            Context c;
            PlayQueue q;
            int seek;
            boolean playing;
            long epoch;
            synchronized (LOCK) {
                c = appCtx;
                q = pending;
                seek = pendingSeekMs;
                playing = pendingPlaying;
                epoch = memoryEpoch;
                pending = null;
            }
            if (c == null || q == null) return;
            PlayQueueStore.save(c, q, seek, playing, epoch);
            diskEpoch = epoch;
        }
    };

    private AsyncPlayQueueWriter() {}

    /** Start writer thread on first real save (unit tests skip Android looper). 2026-07-19 */
    private static Handler ensureHandler() {
        synchronized (LOCK) {
            if (handler != null) return handler;
            thread = new HandlerThread("solar-queue-persist");
            thread.start();
            handler = new Handler(thread.getLooper());
            return handler;
        }
    }

    /** Bump memory epoch (call when queue mutates). */
    public static long bumpEpoch() {
        return ++memoryEpoch;
    }

    public static long memoryEpoch() {
        return memoryEpoch;
    }

    public static long diskEpoch() {
        return diskEpoch;
    }

    /** After restore from disk, align epochs. */
    public static void noteRestoredEpoch(long epoch) {
        if (epoch > 0) {
            memoryEpoch = epoch;
            diskEpoch = epoch;
        }
    }

    /**
     * Schedule a debounced save. Never blocks the caller.
     * 2026-07-19
     */
    public static void scheduleSave(Context ctx, PlayQueue queue, int seekMs, boolean playing) {
        if (ctx == null || queue == null) return;
        synchronized (LOCK) {
            appCtx = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
            pending = queue;
            pendingSeekMs = seekMs;
            pendingPlaying = playing;
            if (memoryEpoch <= diskEpoch) {
                memoryEpoch = diskEpoch + 1;
            }
        }
        Handler h = ensureHandler();
        h.removeCallbacks(FLUSH);
        h.postDelayed(FLUSH, DEBOUNCE_MS);
    }

    /** Flush now (lifecycle) — still off UI if called from UI via post. */
    public static void flushNow() {
        Handler h;
        synchronized (LOCK) {
            if (handler == null) return;
            h = handler;
        }
        h.removeCallbacks(FLUSH);
        h.post(FLUSH);
    }

    /**
     * True when disk may safely replace memory (empty mem, or disk epoch newer).
     * Was: disk.size > mem.size. Reversal: that size heuristic.
     * 2026-07-19
     */
    public static boolean shouldRestoreFromDisk(boolean memEmpty, long diskEpochValue) {
        if (memEmpty) return true;
        return diskEpochValue > memoryEpoch;
    }
}
