package com.solar.launcher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * 2026-07-06 — Stagger non-critical boot helpers so first minutes after boot stay calm.
 * Layman: Solar wakes up in steps instead of firing every daemon at once.
 * Tech: main-looper delayed posts; overlay/rescue stay immediate for modal paint.
 * Reversal: delete and restore eager {@link BootReceiver} / {@link SolarApplication} starts.
 */
public final class SolarBootPacing {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private SolarBootPacing() {}

    /** Post a boot helper after delayMs on the main looper (2026-07-06). */
    public static void schedule(Context context, long delayMs, final Runnable task) {
        if (context == null || task == null) return;
        final Context app = context.getApplicationContext();
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Exception ignored) {}
            }
        }, Math.max(0L, delayMs));
    }

    /**
     * Background bootstrap thread — sleep between heavy I/O chunks (2026-07-06).
     * Layman: pause between big file copies so menus stay responsive.
     */
    public static void pauseBetweenBootstrapSteps() {
        try {
            Thread.sleep(400L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
