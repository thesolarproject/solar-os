package com.solar.launcher.eq;

import android.content.Context;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * 2026-07-15 — Holds live EQ curve and applies FFmpeg af options to IJK players.
 * Layman: remember the EQ and wire it into the player that can do filters.
 * Reversal: disable EQ (flat) → callers use MediaPlayer without af.
 */
public final class SolarEqController {

    private static SolarEqController instance;

    private EqBandModel active = new EqBandModel();
    private EqBandModel abStash;
    private boolean loaded;

    public static synchronized SolarEqController get() {
        if (instance == null) instance = new SolarEqController();
        return instance;
    }

    private SolarEqController() {}

    /** Load prefs once per process. */
    public synchronized void ensureLoaded(Context ctx) {
        if (loaded || ctx == null) return;
        active = EqPresetStore.loadActive(ctx.getApplicationContext());
        loaded = true;
    }

    public synchronized EqBandModel getActive() {
        return active;
    }

    public synchronized void setActive(Context ctx, EqBandModel model) {
        if (model == null) return;
        active = new EqBandModel(model);
        if (ctx != null) EqPresetStore.saveActive(ctx.getApplicationContext(), active);
    }

    public synchronized boolean needsSoftwareEq() {
        return active != null && active.needsSoftwareEq();
    }

    public synchronized boolean isEnabled() {
        return active != null && active.isEnabled();
    }

    /** Stash current curve for A/B toggle. */
    public synchronized void stashForAb() {
        abStash = new EqBandModel(active);
    }

    /** Swap live ↔ stash; returns true if swapped. */
    public synchronized boolean toggleAb(Context ctx) {
        if (abStash == null) {
            stashForAb();
            active.resetFlat();
            if (ctx != null) EqPresetStore.saveActive(ctx.getApplicationContext(), active);
            return true;
        }
        EqBandModel tmp = active;
        active = abStash;
        abStash = tmp;
        if (ctx != null) EqPresetStore.saveActive(ctx.getApplicationContext(), active);
        return true;
    }

    /**
     * Apply lavfi af string before prepareAsync/prepare.
     * Uses player category string option "af" (ffplay-compatible on IJK builds with filters).
     */
    public synchronized void applyToIjk(IjkMediaPlayer player) {
        if (player == null) return;
        String af = EqFilterGraph.buildAf(active);
        if (af == null || af.length() == 0) {
            // Clear prior filter when flat so re-prepare is clean.
            try {
                player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "af", "");
            } catch (Exception ignored) {
            }
            return;
        }
        try {
            player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "af", af);
        } catch (Exception ignored) {
        }
    }

    /** Current filter string (null if none) — for diagnostics. */
    public synchronized String currentAf() {
        return EqFilterGraph.buildAf(active);
    }
}
