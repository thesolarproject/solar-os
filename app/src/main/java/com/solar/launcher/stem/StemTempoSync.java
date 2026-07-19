package com.solar.launcher.stem;

/**
 * Song-to-song tempo match helpers — Song 1 is master.
 * Layman: speed other songs so they sit on Song 1’s pulse without chipmunking.
 * Technical: StemBpm.rateToMatch → IJK SoundTouch when |rate-1| > epsilon; else 1.0 MediaPlayer.
 * Was: no cross-song rate. Reversal: always leave rate=1 and rely on seek drift only.
 * 2026-07-19
 */
public final class StemTempoSync {
    /** Ignore tiny BPM estimate noise. */
    public static final float RATE_EPSILON = 0.02f;

    private StemTempoSync() {}

    /**
     * Rate for songIndex to match master (song 0). Song 0 always 1.0.
     * 2026-07-19
     */
    public static float rateForSong(float masterBpm, float songBpm, int songIndex) {
        if (songIndex <= 0) return 1f;
        float r = StemBpm.rateToMatch(masterBpm, songBpm);
        if (Math.abs(r - 1f) < RATE_EPSILON) return 1f;
        return r;
    }

    /** True when this song needs pitch-preserving stretch (IJK SoundTouch). */
    public static boolean needsSoundTouch(float rate) {
        return Math.abs(rate - 1f) >= RATE_EPSILON;
    }
}
