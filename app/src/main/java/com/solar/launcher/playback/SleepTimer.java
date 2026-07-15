package com.solar.launcher.playback;

import android.os.Handler;

/**
 * 2026-07-15 — Sleep timer for long-form listening (podcasts / audiobooks).
 * Layman: stop playback after 15/30/45 minutes or at the end of this episode.
 * Reversal: cancel() clears the callback — music keeps playing.
 */
public final class SleepTimer {

    public interface Listener {
        void onSleepFire(boolean endOfEpisodeOnly);
    }

    public static final int OFF = 0;
    public static final int MIN_15 = 15 * 60 * 1000;
    public static final int MIN_30 = 30 * 60 * 1000;
    public static final int MIN_45 = 45 * 60 * 1000;
    public static final int END_OF_EPISODE = -1;

    private final Handler handler = new Handler();
    private Listener listener;
    private int mode = OFF;
    private final Runnable fire = new Runnable() {
        @Override
        public void run() {
            Listener l = listener;
            int m = mode;
            mode = OFF;
            if (l != null) l.onSleepFire(m == END_OF_EPISODE);
        }
    };

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public int getMode() {
        return mode;
    }

    public void cancel() {
        handler.removeCallbacks(fire);
        mode = OFF;
    }

    /** Schedule delayMs, or END_OF_EPISODE (fires from completion callback). */
    public void arm(int delayMsOrEnd) {
        cancel();
        mode = delayMsOrEnd;
        if (delayMsOrEnd > 0) {
            handler.postDelayed(fire, delayMsOrEnd);
        }
    }

    /** Call from episode completion when mode == END_OF_EPISODE. */
    public void onEpisodeComplete() {
        if (mode == END_OF_EPISODE) {
            Listener l = listener;
            mode = OFF;
            if (l != null) l.onSleepFire(true);
        }
    }

    public String label() {
        if (mode == OFF) return "Off";
        if (mode == END_OF_EPISODE) return "End of episode";
        if (mode == MIN_15) return "15 min";
        if (mode == MIN_30) return "30 min";
        if (mode == MIN_45) return "45 min";
        return "On";
    }
}
