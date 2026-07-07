package com.solar.launcher;

/**
 * 2026-07-05 — Shared stale overlay mutex heal before hold-to-open gestures.
 * Layman: clears ghost "overlay open" flags so Back/Power long-press can open the menu again.
 * Technical: sysprop-only clear — same ceiling as {@link OverlayKeyGate}; Xposed/root call before arm.
 * Reversal: delete; callers invoke {@link OverlayKeyGate#disarmStaleIfNeeded} directly.
 */
public final class StaleOverlayGate {

    private StaleOverlayGate() {}

    /** Drop stuck opening/active props when ui=0 past the 2s watchdog ceiling. */
    public static void clearIfNeeded() {
        OverlayKeyGate.clearStaleOverlayGatesIfNeeded();
    }
}
