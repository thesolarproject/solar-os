package com.solar.launcher;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

/**
 * 2026-07-11 — A5 Now Playing touch: tap play/pause, swipe skip, hold scrub, swipe-down dismiss.
 * Layman: poke the art to pause; swipe sideways to change songs; hold to rewind/ff; pull down to leave.
 * Tech: MotionEvent classifier on album host; callbacks into MainActivity transport.
 * Reversal: detach listener; NP stays key-only like Y1/Y2.
 */
public final class NowPlayingTouchGestures implements View.OnTouchListener {

    /** Min travel (px) to count as swipe vs tap. */
    public static final int SWIPE_MIN_PX = 40;
    /** Hold before scrub starts (matches media skip long-press). */
    public static final long HOLD_SCRUB_MS = 500L;
    /** Scrub step cadence while holding. */
    public static final long SCRUB_TICK_MS = 200L;

    /** Callbacks into player — keep this class UI-free. */
    public interface Host {
        void onNpTapPlayPause();
        void onNpSwipeNext();
        void onNpSwipePrevious();
        /** Positive deltaMs = forward; negative = rewind. */
        void onNpScrubBy(long deltaMs);
        void onNpSwipeDismiss();
        /**
         * 2026-07-14 — True only after NP context “Scrubbing” (fine cursor mode).
         * Layman: hold-finger scrub stays off until the user picks Scrubbing in options.
         * Tech: gates HOLD_SCRUB_MS arm; default false so edge/center cannot sneak scrub.
         */
        boolean isNpFineScrubArmed();
    }

    private final Host host;
    private final Handler handler;
    private float downX;
    private float downY;
    private long downAt;
    private boolean tracking;
    private boolean scrubArmed;
    private boolean scrubLeft;
    private final Runnable scrubTick = new Runnable() {
        @Override public void run() {
            if (!scrubArmed || host == null) return;
            // 2026-07-11 — Same 5s step as hardware skip hold.
            host.onNpScrubBy(scrubLeft ? -5000L : 5000L);
            handler.postDelayed(this, SCRUB_TICK_MS);
        }
    };
    private final Runnable armScrub = new Runnable() {
        @Override public void run() {
            if (!tracking) return;
            scrubArmed = true;
            scrubTick.run();
        }
    };

    public NowPlayingTouchGestures(Host host) {
        this.host = host;
        this.handler = new Handler(Looper.getMainLooper());
    }

    /** Attach to album/art host when A5; no-op on null. */
    public static void attachIfA5(View target, Host host) {
        if (target == null || host == null || !DeviceFeatures.isA5()) return;
        target.setOnTouchListener(new NowPlayingTouchGestures(host));
        target.setClickable(true);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event == null || host == null) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            // 2026-07-14 — Edge of the panel is navigation (A5EdgeGestures), not scrub/skip.
            // Was: full player panel OnTouch ate edge downs so Back felt like media skip/OK.
            // Reversal: remove isScreenEdgeDown check; NP owns every finger down again.
            if (isScreenEdgeDown(v, event)) {
                tracking = false;
                return false;
            }
            downX = event.getX();
            downY = event.getY();
            downAt = event.getEventTime();
            tracking = true;
            scrubArmed = false;
            // Hold side: left half rewind, right half FF once armed.
            scrubLeft = downX < (v != null ? v.getWidth() : 0) * 0.5f;
            handler.removeCallbacks(armScrub);
            handler.removeCallbacks(scrubTick);
            // 2026-07-14 — Hold-scrub only after context Scrubbing / fine cursor armed.
            // Was: any 500ms hold on NP art started scrub (fought edge Back + OK play/pause).
            // Reversal: always postDelayed(armScrub, HOLD_SCRUB_MS).
            if (host.isNpFineScrubArmed()) {
                handler.postDelayed(armScrub, HOLD_SCRUB_MS);
            }
            return true;
        }
        if (!tracking) return false;
        if (action == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            // Cancel hold-scrub once finger clearly swipes.
            if (!scrubArmed && (Math.abs(dx) > SWIPE_MIN_PX || Math.abs(dy) > SWIPE_MIN_PX)) {
                handler.removeCallbacks(armScrub);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            handler.removeCallbacks(armScrub);
            handler.removeCallbacks(scrubTick);
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            boolean wasScrub = scrubArmed;
            scrubArmed = false;
            tracking = false;
            if (action == MotionEvent.ACTION_CANCEL || wasScrub) {
                return true;
            }
            Gesture g = classify(dx, dy, event.getEventTime() - downAt);
            apply(g);
            return true;
        }
        return false;
    }

    /** Pure classifier for unit tests — no View needed. */
    public static Gesture classify(float dx, float dy, long heldMs) {
        float adx = Math.abs(dx);
        float ady = Math.abs(dy);
        if (ady > SWIPE_MIN_PX && ady > adx && dy > 0) {
            return Gesture.DISMISS;
        }
        if (adx > SWIPE_MIN_PX && adx > ady) {
            return dx < 0 ? Gesture.NEXT : Gesture.PREVIOUS;
        }
        if (heldMs < HOLD_SCRUB_MS) {
            return Gesture.TAP_PLAY_PAUSE;
        }
        return Gesture.NONE;
    }

    private void apply(Gesture g) {
        if (g == Gesture.TAP_PLAY_PAUSE) host.onNpTapPlayPause();
        else if (g == Gesture.NEXT) host.onNpSwipeNext();
        else if (g == Gesture.PREVIOUS) host.onNpSwipePrevious();
        else if (g == Gesture.DISMISS) host.onNpSwipeDismiss();
    }

    /**
     * 2026-07-14 — True when the finger started in the L/R screen edge band.
     * Layman: side of the glass = Back, not Next/Prev.
     * Tech: view-local → screen via getLocationOnScreen; {@link A5EdgeGestures#edgeAt}.
     */
    static boolean isScreenEdgeDown(View v, MotionEvent event) {
        if (v == null || event == null || v.getContext() == null) return false;
        try {
            int[] loc = new int[2];
            v.getLocationOnScreen(loc);
            float sx = event.getX() + loc[0];
            float sy = event.getY() + loc[1];
            android.util.DisplayMetrics dm = v.getContext().getResources().getDisplayMetrics();
            int w = dm != null ? dm.widthPixels : 0;
            int h = dm != null ? dm.heightPixels : 0;
            A5EdgeGestures.Edge e = A5EdgeGestures.edgeAt(sx, sy, w, h);
            return e == A5EdgeGestures.Edge.LEFT || e == A5EdgeGestures.Edge.RIGHT;
        } catch (Throwable t) {
            return false;
        }
    }

    public enum Gesture {
        NONE,
        TAP_PLAY_PAUSE,
        NEXT,
        PREVIOUS,
        DISMISS
    }
}
