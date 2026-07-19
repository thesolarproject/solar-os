package com.solar.launcher.stem;

/**
 * Pure Stem Player control math — gain steps + Gen1 bar-loop ladder + wheel polarity.
 * Layman: CW turns volume up / shortens the loop; CCW turns volume down / lengthens the loop.
 * Technical: raw wheelUp=+1 from MainActivity; volume uses negated steps; loop uses raw steps
 * so CW/CCW feel opposite across the two Center modes.
 * Was: same sign for gain and bars (both felt backwards on volume). Reversal: drop polarity helpers.
 * 2026-07-19
 */
public final class StemControls {
    /** Clicks for 0→1 gain — under one full Y1/Y2 wheel turn. */
    public static final int GAIN_CLICKS_FULL = 14;
    public static final float GAIN_STEP = 1f / GAIN_CLICKS_FULL;

    /** Gen1 vertical LED bar lengths (bars). All-lit = no loop on hardware; we clear separately. */
    public static final float[] LOOP_BARS = {
            0.25f, 0.5f, 1f, 2f, 4f, 8f
    };

    /** Default loop when Center dials into loop mode — Gen1 “1-bar loop”. */
    public static final float DEFAULT_LOOP_BARS = 1f;

    private StemControls() {}

    /** Clamp gain to 0..1. */
    public static float clampGain(float g) {
        if (g < 0f) return 0f;
        if (g > 1f) return 1f;
        return g;
    }

    /**
     * Map hardware wheel steps (up=+1 / down=-1) to volume steps.
     * CW raises volume, CCW lowers — negate raw so CW≈wheel-down becomes louder.
     * 2026-07-19
     */
    public static int volumeStepsFromWheel(int rawWheelSteps) {
        return -rawWheelSteps;
    }

    /**
     * Map hardware wheel steps to loop-bar ladder steps.
     * CW shortens loop, CCW lengthens — keep raw sign (opposite of volume polarity).
     * 2026-07-19
     */
    public static int loopStepsFromWheel(int rawWheelSteps) {
        return rawWheelSteps;
    }

    /** Apply wheel steps to gain; positive steps = louder. */
    public static float nudgeGain(float current, int steps) {
        return clampGain(current + steps * GAIN_STEP);
    }

    /** Index of closest LOOP_BARS entry (0..length-1). */
    public static int loopIndexForBars(float bars) {
        int best = 0;
        float bestD = Math.abs(LOOP_BARS[0] - bars);
        for (int i = 1; i < LOOP_BARS.length; i++) {
            float d = Math.abs(LOOP_BARS[i] - bars);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Step loop bar ladder; positive = longer. Returns new bars value. */
    public static float nudgeLoopBars(float currentBars, int steps) {
        int idx = loopIndexForBars(currentBars) + steps;
        if (idx < 0) idx = 0;
        if (idx >= LOOP_BARS.length) idx = LOOP_BARS.length - 1;
        return LOOP_BARS[idx];
    }

    /** How many lit dots (1..8) for gain 0..1. */
    public static int dotsForGain(float gain, int maxDots) {
        if (maxDots < 1) return 0;
        float g = clampGain(gain);
        if (g <= 0.001f) return 0;
        int n = Math.round(g * maxDots);
        if (n < 1) n = 1;
        if (n > maxDots) n = maxDots;
        return n;
    }

    /** How many lit dots for loop bars (maps ladder onto maxDots). */
    public static int dotsForLoopBars(float bars, int maxDots) {
        if (maxDots < 1) return 0;
        int idx = loopIndexForBars(bars);
        // Spread 6 ladder steps across maxDots LEDs.
        int n = 1 + (idx * (maxDots - 1)) / (LOOP_BARS.length - 1);
        if (n < 1) n = 1;
        if (n > maxDots) n = maxDots;
        return n;
    }

    /**
     * When focusing a stem, use loop wheel only if audio is looping AND that stem
     * already joined loop-control; otherwise volume (fast path for non-looping stems).
     * 2026-07-19
     */
    public static boolean wheelLoopModeForStem(boolean audioLooping, boolean stemInLoopCtrl) {
        return audioLooping && stemInLoopCtrl;
    }

    /**
     * Center while audio loops: stop if this stem is in the loop-ctrl set; else join it.
     * Returns true when the whole loop should stop.
     * 2026-07-19
     */
    public static boolean centerShouldStopLoop(boolean audioLooping, boolean stemInLoopCtrl) {
        return audioLooping && stemInLoopCtrl;
    }

    /**
     * Hold-Center volume peek is only for stems already in loop-control while audio loops.
     * 2026-07-19
     */
    public static boolean centerHoldVolumeEligible(boolean audioLooping, boolean stemInLoopCtrl) {
        return audioLooping && stemInLoopCtrl;
    }

    /**
     * Wheel uses volume while Center is held for a peek, else follows normal wheelLoopMode.
     * 2026-07-19
     */
    public static boolean wheelUsesVolume(boolean wheelLoopMode, boolean centerHoldVolumeActive) {
        if (centerHoldVolumeActive) return true;
        return !wheelLoopMode;
    }

    /** True only when both side keys are held (Stem exit gesture). 2026-07-19 */
    public static boolean stemExitBothSidesHeld(boolean prevDown, boolean nextDown) {
        return prevDown && nextDown;
    }

    /**
     * Stem key: true if this press should cycle song (zone already focused + multi).
     * Layman: only flip song when that pad is already the one you are on.
     * Technical: compare focused vs pressed BEFORE updating focus; songCount>1 required.
     * 2026-07-19
     */
    public static boolean stemKeyShouldCycleSong(int activeZone, int pressedZone, int songCount) {
        return songCount > 1 && activeZone == pressedZone;
    }

    /**
     * Face LEDs: show loop-bar fill only while wheel is in loop mode (not volume).
     * Layman: when turning volume, beads follow loudness even if a loop is running.
     * Was: face used audioLoop||wheelLoopMode so focused arm stuck on bar LEDs. Reversal: that OR.
     * 2026-07-19
     */
    public static boolean faceShowsLoopBars(boolean wheelLoopMode, boolean centerHoldVolumeActive) {
        return !wheelUsesVolume(wheelLoopMode, centerHoldVolumeActive);
    }

    /** Hold delay before hip-hop stutter starts (short press = focus/cycle). 2026-07-19 */
    // Was: 220ms — normal taps often exceeded it → stutter ate focus/cycle (no song switch).
    // Reversal: STEM_STUTTER_HOLD_MS = 220L.
    public static final long STEM_STUTTER_HOLD_MS = 480L;

    /** Default chop step for hold-stutter (1/8 bar). 2026-07-19 */
    public static final int DEFAULT_STUTTER_CHOP_STEP = 2;
}
