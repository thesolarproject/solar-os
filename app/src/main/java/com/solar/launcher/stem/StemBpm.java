package com.solar.launcher.stem;

/**
 * BPM + beatgrid helpers for Stem mashup / chop.
 * Layman: guess the song’s pulse so chops land on the beat and songs can match tempo.
 * Technical: duration heuristic → BPM; rate clamp for SoundTouch; chop slice ms from bar fraction.
 * Was: fixed 2000 ms/bar only. Reversal: ignore StemBpm, keep DEFAULT_MS_PER_BAR.
 * 2026-07-19
 */
public final class StemBpm {
    public static final float DEFAULT_BPM = 120f;
    public static final float MIN_RATE = 0.85f;
    public static final float MAX_RATE = 1.15f;
    /** Chop ladder as fraction of one bar (0 = stutter off / whole). */
    public static final float[] CHOP_FRAC = { 0f, 1f / 16f, 1f / 8f, 1f / 4f, 1f / 2f };
    /** Classic screw rates (pitch follows). */
    public static final float[] SCREW_RATES = { 1f, 0.85f, 0.7f, 0.55f };

    private StemBpm() {}

    /** ms per bar at BPM (4/4). */
    public static int msPerBar(float bpm) {
        float b = bpm > 30f && bpm < 300f ? bpm : DEFAULT_BPM;
        return Math.max(200, Math.round(240000f / b));
    }

    /**
     * Rough BPM from lead duration — assume ~4-min pop ≈ 120, scale by length.
     * Better than nothing without onset detection on Y1.
     * 2026-07-19
     */
    public static float estimateFromDurationMs(int durationMs) {
        if (durationMs < 15_000) return DEFAULT_BPM;
        // Assume ~96 bars in a typical track; bpm = bars*60 / durationSec * 4 beats? simplify:
        // Prefer mid-tempo for 3–5 min songs.
        float sec = durationMs / 1000f;
        if (sec < 90f) return 128f;
        if (sec < 150f) return 120f;
        if (sec < 240f) return 112f;
        return 100f;
    }

    /** Playback rate so otherBpm matches masterBpm (clamped). */
    public static float rateToMatch(float masterBpm, float otherBpm) {
        float m = masterBpm > 30f ? masterBpm : DEFAULT_BPM;
        float o = otherBpm > 30f ? otherBpm : DEFAULT_BPM;
        float r = m / o;
        if (r < MIN_RATE) return MIN_RATE;
        if (r > MAX_RATE) return MAX_RATE;
        return r;
    }

    public static int clampChopStep(int step) {
        if (step < 0) return 0;
        if (step >= CHOP_FRAC.length) return CHOP_FRAC.length - 1;
        return step;
    }

    public static int nudgeChopStep(int current, int steps) {
        return clampChopStep(current + steps);
    }

    /** Slice length in ms; 0 = chop stutter off. */
    public static int chopSliceMs(float bpm, int chopStep) {
        int step = clampChopStep(chopStep);
        float frac = CHOP_FRAC[step];
        if (frac <= 0.001f) return 0;
        return Math.max(40, Math.round(msPerBar(bpm) * frac));
    }

    public static int screwIndexForRate(float rate) {
        int best = 0;
        float bestD = Math.abs(SCREW_RATES[0] - rate);
        for (int i = 1; i < SCREW_RATES.length; i++) {
            float d = Math.abs(SCREW_RATES[i] - rate);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    public static float nudgeScrewRate(float current, int steps) {
        int idx = screwIndexForRate(current) + steps;
        if (idx < 0) idx = 0;
        if (idx >= SCREW_RATES.length) idx = SCREW_RATES.length - 1;
        return SCREW_RATES[idx];
    }

    public static String chopLabel(int chopStep) {
        int s = clampChopStep(chopStep);
        if (s == 0) return "Chop off";
        if (s == 1) return "1/16";
        if (s == 2) return "1/8";
        if (s == 3) return "1/4";
        return "1/2";
    }
}
