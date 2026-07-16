package com.solar.launcher;

import android.view.View;
import android.widget.ListView;

/**
 * 2026-07-16 — Coalesce scrollwheel ticks for long ListViews (Y1/Y2 track lists).
 * Layman: spinning the dial fast used to queue every notch as full layout work; now ticks
 * merge into one jump per frame so the highlight keeps up instead of lagging behind.
 * Technical: signed step accumulator + single postOnAnimation flush; no alloc on accept.
 * Reversal: call apply immediately from the key path (old one-ensure-per-keyevent backlog).
 */
final class ListWheelCoalescer {

    /** Cap catch-up jump per frame so a huge backlog does not teleport past the whole library. */
    static final int MAX_STEPS_PER_FLUSH = 16;

    private int pendingSteps;
    private boolean flushPosted;
    private final Runnable flushRunnable;
    private View host;
    private Apply apply;

    interface Apply {
        /** Apply net signed row steps (negative = up). Return true if selection moved. */
        boolean applySteps(int signedSteps);
    }

    ListWheelCoalescer() {
        flushRunnable = new Runnable() {
            @Override
            public void run() {
                flushPosted = false;
                int steps = pendingSteps;
                pendingSteps = 0;
                Apply a = apply;
                if (a == null || steps == 0) return;
                // Catch-up: large backlog → multi-row jump (still capped).
                int signed = clampSteps(steps);
                a.applySteps(signed);
                // More ticks arrived during apply — schedule another frame.
                if (pendingSteps != 0) {
                    scheduleFlush();
                }
            }
        };
    }

    /** Bind list + callback (call when adapter is shown). */
    void bind(ListView list, Apply apply) {
        this.host = list;
        this.apply = apply;
    }

    void clear() {
        pendingSteps = 0;
        flushPosted = false;
        if (host != null) {
            host.removeCallbacks(flushRunnable);
        }
        host = null;
        apply = null;
    }

    /**
     * Queue one wheel notch (+1 down / −1 up). Returns true if consumed (always when bound).
     * Layman: cheap — just add to a counter and ask for one paint-frame later.
     */
    boolean offer(int direction) {
        if (direction == 0 || apply == null || host == null) return false;
        pendingSteps += direction > 0 ? 1 : -1;
        scheduleFlush();
        return true;
    }

    /**
     * Queue already-amplified flywheel steps (from {@link WheelPhysics}).
     * Prefer this when a single keyevent already computed multi-row motion.
     */
    boolean offerSteps(int signedSteps) {
        if (signedSteps == 0 || apply == null || host == null) return false;
        pendingSteps += signedSteps;
        scheduleFlush();
        return true;
    }

    private void scheduleFlush() {
        if (flushPosted || host == null) return;
        flushPosted = true;
        // API 16+ postOnAnimation = next vsync; API 17 Y1/Y2 support it.
        host.postOnAnimation(flushRunnable);
    }

    /** Pure clamp for tests. */
    static int clampSteps(int steps) {
        if (steps > MAX_STEPS_PER_FLUSH) return MAX_STEPS_PER_FLUSH;
        if (steps < -MAX_STEPS_PER_FLUSH) return -MAX_STEPS_PER_FLUSH;
        return steps;
    }
}
