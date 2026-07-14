package com.solar.launcher.overlay;

/**
 * 2026-07-08 — Center/OK grace after a chip opens a sub-tier.
 * Layman: the finger-lift that opened Power must not also tap Restart.
 * Matches app OverlayCenterActivation.SUB_TIER_CENTER_GRACE_MS.
 * Reversal: activate on KEY_DOWN again (hold OK may chain into reboot).
 */
public final class OverlayCenterGrace {

    /** Swallow center UP for this long after tier paint / chip drill. */
    public static final long SUB_TIER_CENTER_GRACE_MS = 595L;

    private OverlayCenterGrace() {}

    /**
     * 2026-07-08 — Whether center/play should fire activateFocused.
     * Swallow DOWN + auto-repeat; honor UP only after grace since last tier stamp.
     */
    public static boolean shouldActivateOnEvent(boolean isKeyUp, boolean isAutoRepeat,
            long nowUptimeMs, long subTierChangedAtUptimeMs, long graceMs) {
        if (isAutoRepeat) return false;
        if (!isKeyUp) return false;
        if (subTierChangedAtUptimeMs <= 0L) return true;
        return nowUptimeMs - subTierChangedAtUptimeMs >= graceMs;
    }
}
