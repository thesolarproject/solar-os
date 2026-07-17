package com.solar.launcher;

import android.os.SystemClock;
import android.view.View;
import android.widget.ListView;

/**
 * Coalesce scrollwheel ticks for long ListViews (Y1/Y2 track lists, 10k–50k tracks).
 *
 * <p>Layman: spinning the dial fast used to queue every notch as full layout work; now ticks
 * merge into <b>one jump per frame</b> so the highlight keeps up (iPod Classic feel).
 *
 * <p>2026-07-17 — Hard-stop when the finger pauses or mic says lift: drop leftover queue so
 * the list does not walk after release. Optional {@link LiveGate} (mic volume/HF) kills
 * ghost flushes mid-frame on huge libraries.
 *
 * <p>Technical: signed step accumulator + single postOnAnimation flush; pending capped to one
 * frame; idle gap clears pending. Reversal: apply immediately from the key path.
 */
final class ListWheelCoalescer {

    /**
     * Cap jump per frame — one selection change; letter jump path uses section index instead.
     * Keep low so 50k lists never apply multi-frame backlog after stop.
     */
    static final int MAX_STEPS_PER_FLUSH = 6;

    /**
     * Gap since last offer that means the finger stopped — drop pending before accepting more.
     */
    static final long IDLE_CLEAR_MS = 75L;

    private int pendingSteps;
    private boolean flushPosted;
    private long lastOfferUptimeMs = -1L;
    private final Runnable flushRunnable;
    private View host;
    private Apply apply;
    private LiveGate liveGate;

    interface Apply {
        /** Apply net signed row steps (negative = up). Return true if selection moved. */
        boolean applySteps(int signedSteps);
    }

    /**
     * Optional mic / activity gate. When {@link #allowFlush()} is false, pending steps are
     * dropped instead of applied (finger lifted — no ghost walk).
     */
    interface LiveGate {
        boolean allowFlush();
    }

    ListWheelCoalescer() {
        flushRunnable = new Runnable() {
            @Override
            public void run() {
                flushPosted = false;
                LiveGate gate = liveGate;
                if (gate != null && !gate.allowFlush()) {
                    // Mic/quiet: finger gone — discard backlog, do not walk the list.
                    pendingSteps = 0;
                    return;
                }
                int steps = pendingSteps;
                pendingSteps = 0;
                Apply a = apply;
                if (a == null || steps == 0) return;
                int signed = clampSteps(steps);
                a.applySteps(signed);
                // Only continue if new notches arrived during apply (live spin).
                if (pendingSteps != 0) {
                    if (gate != null && !gate.allowFlush()) {
                        pendingSteps = 0;
                        return;
                    }
                    scheduleFlush();
                }
            }
        };
    }

    void setLiveGate(LiveGate gate) {
        this.liveGate = gate;
    }

    /** Bind list + callback (call when adapter is shown). */
    void bind(ListView list, Apply apply) {
        this.host = list;
        this.apply = apply;
    }

    void clear() {
        dropPending();
        host = null;
        apply = null;
        lastOfferUptimeMs = -1L;
    }

    /**
     * Drop any unflushed steps and cancel a scheduled flush.
     * Call when the dial pauses, mic lift, stale keyevents, or the list is torn down.
     */
    void dropPending() {
        pendingSteps = 0;
        flushPosted = false;
        if (host != null) {
            host.removeCallbacks(flushRunnable);
        }
    }

    boolean offer(int direction) {
        return offerSteps(direction > 0 ? 1 : (direction < 0 ? -1 : 0),
                SystemClock.uptimeMillis());
    }

    boolean offerSteps(int signedSteps) {
        return offerSteps(signedSteps, SystemClock.uptimeMillis());
    }

    /**
     * @param nowMs {@link SystemClock#uptimeMillis()} (injectable for tests)
     */
    boolean offerSteps(int signedSteps, long nowMs) {
        if (signedSteps == 0 || apply == null || host == null) return false;
        LiveGate gate = liveGate;
        if (gate != null && !gate.allowFlush()) {
            // Drop ghost backlog only — still accept this live KEY offer.
            pendingSteps = 0;
            if (host != null) {
                host.removeCallbacks(flushRunnable);
            }
            flushPosted = false;
        }
        if (lastOfferUptimeMs >= 0L && (nowMs - lastOfferUptimeMs) >= IDLE_CLEAR_MS) {
            pendingSteps = 0;
            if (host != null) {
                host.removeCallbacks(flushRunnable);
            }
            flushPosted = false;
        }
        lastOfferUptimeMs = nowMs;
        pendingSteps += signedSteps;
        pendingSteps = clampSteps(pendingSteps);
        scheduleFlush();
        return true;
    }

    int pendingStepsForTest() {
        return pendingSteps;
    }

    private void scheduleFlush() {
        if (flushPosted || host == null) return;
        flushPosted = true;
        host.postOnAnimation(flushRunnable);
    }

    static int clampSteps(int steps) {
        if (steps > MAX_STEPS_PER_FLUSH) return MAX_STEPS_PER_FLUSH;
        if (steps < -MAX_STEPS_PER_FLUSH) return -MAX_STEPS_PER_FLUSH;
        return steps;
    }
}
