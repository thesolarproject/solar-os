package com.solar.launcher;

/**
 * Microphone as scroll <b>ally</b> only — never sets direction.
 *
 * <ul>
 *   <li><b>Boost</b> — multiplies notch impulse while scrape is firm and KEY is fresh</li>
 *   <li><b>Ghost backlog kill</b> — after a spin with real mic contact, quiet means drop
 *       <em>queued</em> coalescer steps only — never blocks a live KEY notch</li>
 * </ul>
 *
 * <p>HW KEY owns CW/CCW. A live wheel edge must always move the UI even if the mic is
 * silent (quiet room, no permission, probe not warm yet).
 */
public final class MicScrollBoost {

    public static final float SCRATCH_FLOOR = 0.28f;
    public static final float SCRATCH_FULL = 1.8f;
    public static final float MAX_BOOST = 1.45f;
    public static final float MIN_BOOST = 1.04f;
    public static final long KEY_FRESH_MS = 180L;

    /**
     * After last notch, if mic was in contact and is quiet this long → drop backlog only.
     */
    public static final long LIFT_QUIET_MS = 70L;
    /** How long after a notch we still watch for lift (ms). */
    public static final long GHOST_WATCH_MS = 280L;
    /** Live KEY grace — never treat as ghost while a notch is this fresh. */
    public static final long LIVE_KEY_MS = 80L;

    private float scratch;
    private float volume;
    private float hf;
    private long lastKeyMs = -1L;
    private long quietSinceMs = -1L;
    private boolean sawContactThisSpin;
    private boolean lastSampleContact;

    public void onScratch(float scratchAboveFloor) {
        onFeatures(scratchAboveFloor, scratchAboveFloor * 0.85f, scratchAboveFloor);
    }

    public void onFeatures(float volume, float hf, float fused) {
        if (volume < 0f) volume = 0f;
        if (hf < 0f) hf = 0f;
        if (fused < 0f) fused = 0f;
        lastSampleContact = contactFromLevels(volume, hf);
        this.volume = smoothUp(this.volume, volume);
        this.hf = smoothUp(this.hf, hf);
        this.scratch = smoothUp(this.scratch, fused);
        if (lastSampleContact) {
            sawContactThisSpin = true;
            quietSinceMs = -1L;
        }
    }

    public void onHardwareNotch(long nowMs) {
        lastKeyMs = nowMs;
        // Fresh KEY always re-arms; do not start quiet timer until samples arrive quiet.
        quietSinceMs = -1L;
        if (lastSampleContact) {
            sawContactThisSpin = true;
        }
    }

    public float boost(long nowMs) {
        if (lastKeyMs < 0 || (nowMs - lastKeyMs) > KEY_FRESH_MS) return 1f;
        if (scratch < SCRATCH_FLOOR) return 1f;
        float t = (scratch - SCRATCH_FLOOR) / (SCRATCH_FULL - SCRATCH_FLOOR);
        if (t > 1f) t = 1f;
        if (t < 0f) t = 0f;
        return MIN_BOOST + (MAX_BOOST - MIN_BOOST) * t;
    }

    public boolean isFingerContact() {
        return lastSampleContact;
    }

    /**
     * True only to drop <b>queued backlog</b> after a real scrape-then-lift.
     * Never true within {@link #LIVE_KEY_MS} of a KEY (live notches always apply).
     * Never true if mic never saw contact this spin (quiet room / no mic).
     */
    public boolean shouldDropGhostScroll(long nowMs) {
        if (lastKeyMs < 0L) return false;
        long sinceKey = nowMs - lastKeyMs;
        // Live KEY — never block.
        if (sinceKey < LIVE_KEY_MS) return false;
        // Outside watch window — flywheel/coalescer idle clear owns residual.
        if (sinceKey > GHOST_WATCH_MS) return false;
        // Without prior mic contact, KEY-only scroll must keep working.
        if (!sawContactThisSpin) return false;
        if (lastSampleContact) {
            quietSinceMs = -1L;
            return false;
        }
        if (quietSinceMs < 0L) {
            quietSinceMs = nowMs;
            return false;
        }
        return (nowMs - quietSinceMs) >= LIFT_QUIET_MS;
    }

    static boolean contactFromLevels(float volume, float hf) {
        float fused = Math.max(volume, hf) * 0.65f + Math.min(volume, hf) * 0.35f;
        return volume >= MicScratchSense.VOLUME_CONTACT
                || hf >= MicScratchSense.HF_CONTACT
                || fused >= MicScratchSense.FUSED_CONTACT;
    }

    public float getScratch() {
        return scratch;
    }

    public float getVolume() {
        return volume;
    }

    public float getHf() {
        return hf;
    }

    public void reset() {
        scratch = 0f;
        volume = 0f;
        hf = 0f;
        lastKeyMs = -1L;
        quietSinceMs = -1L;
        sawContactThisSpin = false;
        lastSampleContact = false;
    }

    /** Soft clear after backlog drop — keep lastKey so live follow-up notches still work. */
    public void clearContactSpin() {
        sawContactThisSpin = false;
        quietSinceMs = -1L;
        lastSampleContact = false;
        scratch = 0f;
        volume = 0f;
        hf = 0f;
    }

    private static float smoothUp(float cur, float next) {
        if (next > cur) return cur * 0.4f + next * 0.6f;
        return cur * 0.82f + next * 0.18f;
    }
}
