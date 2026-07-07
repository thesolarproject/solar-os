package com.solar.launcher;

import android.content.Context;

/**
 * 2026-07-05 — Rockbox handoff prep: unified keymap + native lib sync before launcher switch.
 * Rockbox calls switch-to-stock.sh with no reboot — see LauncherSwitch handoff contract.
 * When changing: Y1KeymapSync + RockboxLibSync; sync-y1-assets.sh for ROM/asset parity.
 * Reversal: stop prepareForRockboxSwitch; Rockbox switch may use stale keylayout or codecs.
 */
public final class RockboxCoexistence {
    private RockboxCoexistence() {}

    /** Run before Settings → Switch to Rockbox (keymap + native libs). */
    public static void prepareForRockboxSwitch(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        Y1KeymapSync.ensureUnified(app);
        RockboxLibSync.syncBeforeRockboxSwitch(app);
    }

    /** Solar boot: seed ROM scripts from assets and sync keymap/libs if needed. */
    public static void ensureOnSolarStart(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        Y1KeymapSync.ensureUnified(app);
        RockboxLibSync.ensureOnSolarStart(app);
    }
}
