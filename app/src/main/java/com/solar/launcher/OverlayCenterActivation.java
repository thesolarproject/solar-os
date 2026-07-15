package com.solar.launcher;

/**
 * 2026-07-08 — Decide when overlay center/OK should fire {@code activateFocused()}.
 * Was: KEY_DOWN activated Power chip and could auto-repeat into Restart → reboot.
 * Now: KEY_UP activates after a short tier-transition grace (matches APP_MENU / in-app menu).
 * Reversal: return true on DOWN again and ignore grace (hold OK on Power may restart).
 */
public final class OverlayCenterActivation {

    /**
     * 2026-07-08 — Swallow release that opened a submenu so focus on Restart never auto-fires.
     * Matches APP_MENU_CENTER_GRACE_MS order of magnitude.
     */
    public static final long SUB_TIER_CENTER_GRACE_MS = 595L;

    private OverlayCenterActivation() {}

    /**
     * 2026-07-08 — Whether this center/play event should activate the focused chip or list row.
     * Swallow DOWN (and auto-repeats); honor UP only after {@code graceMs} since last tier paint.
     */
    public static boolean shouldActivateOnEvent(boolean isKeyUp, boolean isAutoRepeat,
            long nowUptimeMs, long subTierChangedAtUptimeMs, long graceMs) {
        // 2026-07-08 — Held OK sends repeat DOWNs; never treat those as taps.
        if (isAutoRepeat) return false;
        // 2026-07-08 — Activation only on finger lift so one press cannot chain tiers.
        if (!isKeyUp) return false;
        if (subTierChangedAtUptimeMs <= 0L) return true;
        // 2026-07-08 — Release that opened Power/Wi‑Fi/confirm must not pick the new row 0.
        return nowUptimeMs - subTierChangedAtUptimeMs >= graceMs;
    }
}
